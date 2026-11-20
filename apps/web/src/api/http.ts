const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'
const tokenKey = 'boxue.accessToken'
const noRefreshPaths = [
  '/auth/login',
  '/auth/register',
  '/auth/refresh',
  '/auth/verify-email',
  '/auth/resend-verification',
  '/auth/forgot-password',
  '/auth/reset-password'
]

export interface ApiErrorBody {
  code: string
  message: string
  details?: Record<string, unknown>
  traceId?: string
}

export class ApiRequestError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly details?: Record<string, unknown>
  ) {
    super(message)
    this.name = 'ApiRequestError'
  }
}

export function getAccessToken(): string | null {
  return sessionStorage.getItem(tokenKey)
}

export function setAccessToken(token: string | null) {
  if (token) {
    sessionStorage.setItem(tokenKey, token)
  } else {
    sessionStorage.removeItem(tokenKey)
  }
}

function readCookie(name: string): string | null {
  const prefix = name + '='
  for (const item of document.cookie.split(';')) {
    const value = item.trim()
    if (value.startsWith(prefix)) {
      return decodeURIComponent(value.substring(prefix.length))
    }
  }
  return null
}

async function ensureCsrfToken(): Promise<string | null> {
  const existing = readCookie('boxue_csrf')
  if (existing) {
    return existing
  }
  const response = await fetch(`${baseUrl}/auth/csrf`, {
    method: 'GET',
    credentials: 'include'
  })
  if (!response.ok) {
    return null
  }
  const data = (await response.json()) as { csrfToken?: string }
  return data.csrfToken ?? readCookie('boxue_csrf')
}

async function withRefreshLock<T>(task: () => Promise<T>): Promise<T> {
  const locks = (navigator as unknown as { locks?: { request: (name: string, cb: () => Promise<T>) => Promise<T> } }).locks
  if (locks?.request) {
    return locks.request('boxue-refresh', task)
  }
  const key = 'boxue.refresh.lock'
  const id = crypto.randomUUID()
  const deadline = Date.now() + 10000
  while (Date.now() < deadline) {
    const current = readLock(key)
    if (!current || current.expires < Date.now()) {
      localStorage.setItem(key, JSON.stringify({ id, expires: Date.now() + 15000 }))
      if (readLock(key)?.id === id) {
        try {
          return await task()
        } finally {
          if (readLock(key)?.id === id) {
            localStorage.removeItem(key)
          }
        }
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 100))
  }
  throw new Error('refresh lock timeout')
}

function readLock(key: string): { id: string; expires: number } | null {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as { id: string; expires: number }) : null
  } catch {
    return null
  }
}

let refreshPromise: Promise<boolean> | null = null

async function refreshAccessToken(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = withRefreshLock(async () => {
      const csrfToken = await ensureCsrfToken()
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'X-Client-Platform': 'web'
      }
      if (csrfToken) {
        headers['X-CSRF-Token'] = csrfToken
      }
      const response = await fetch(`${baseUrl}/auth/refresh`, {
        method: 'POST',
        credentials: 'include',
        headers,
        body: '{}'
      })
      if (!response.ok) {
        return false
      }
      const data = (await response.json()) as { accessToken?: string }
      if (!data.accessToken) {
        return false
      }
      setAccessToken(data.accessToken)
      return true
    }).catch(() => false).finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
  allowRefresh = true
): Promise<T> {
  const headers = new Headers(init.headers)
  if (!(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  headers.set('X-Client-Platform', 'web')

  const token = getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers,
    credentials: 'include'
  })

  if (response.status === 401 && allowRefresh && !noRefreshPaths.some((item) => path.startsWith(item))) {
    if (await refreshAccessToken()) {
      return apiFetch<T>(path, init, false)
    }
  }

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ApiErrorBody | null
    throw new ApiRequestError(
      response.status,
      body?.code ?? 'HTTP_ERROR',
      body?.message ?? `请求失败：${response.status}`,
      body?.details
    )
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}


export async function apiFetchBlob(
  path: string,
  init: RequestInit = {},
  allowRefresh = true
): Promise<Blob> {
  const headers = new Headers(init.headers)
  headers.set('X-Client-Platform', 'web')

  const token = getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers,
    credentials: 'include'
  })

  if (response.status === 401 && allowRefresh) {
    if (await refreshAccessToken()) {
      return apiFetchBlob(path, init, false)
    }
  }

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ApiErrorBody | null
    throw new ApiRequestError(
      response.status,
      body?.code ?? 'HTTP_ERROR',
      body?.message ?? `请求失败：${response.status}`,
      body?.details
    )
  }

  return response.blob()
}
