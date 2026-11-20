const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'
const tokenKey = 'boxue.admin.accessToken'

export interface ApiErrorBody {
  code: string
  message: string
  details?: Record<string, unknown>
  traceId?: string
}

export class AdminApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly details?: Record<string, unknown>
  ) {
    super(message)
    this.name = 'AdminApiError'
  }
}

export function getAdminAccessToken(): string | null {
  return sessionStorage.getItem(tokenKey)
}

export function setAdminAccessToken(token: string | null) {
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

async function ensureAdminCsrfToken(): Promise<string | null> {
  const existing = readCookie('boxue_admin_csrf')
  if (existing) {
    return existing
  }
  const response = await fetch(`${baseUrl}/admin/auth/csrf`, {
    method: 'GET',
    credentials: 'include'
  })
  if (!response.ok) {
    return null
  }
  const data = (await response.json()) as { csrfToken?: string }
  return data.csrfToken ?? readCookie('boxue_admin_csrf')
}

async function withAdminRefreshLock<T>(task: () => Promise<T>): Promise<T> {
  const locks = (navigator as unknown as { locks?: { request: (name: string, cb: () => Promise<T>) => Promise<T> } }).locks
  if (locks?.request) {
    return locks.request('boxue-admin-refresh', task)
  }
  const key = 'boxue.admin.refresh.lock'
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
  throw new Error('admin refresh lock timeout')
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

async function refreshAdminToken(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = withAdminRefreshLock(async () => {
      const csrfToken = await ensureAdminCsrfToken()
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'X-Client-Platform': 'admin'
      }
      if (csrfToken) {
        headers['X-CSRF-Token'] = csrfToken
      }
      const response = await fetch(`${baseUrl}/admin/auth/refresh`, {
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
      setAdminAccessToken(data.accessToken)
      return true
    }).catch(() => false).finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

export async function adminFetch<T>(
  path: string,
  init: RequestInit = {},
  allowRefresh = true
): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')
  headers.set('X-Client-Platform', 'admin')

  const token = getAdminAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers,
    credentials: 'include'
  })

  const isLogin = path.startsWith('/admin/auth/login')
  const isRefresh = path.startsWith('/admin/auth/refresh')
  if (response.status === 401 && allowRefresh && !isLogin && !isRefresh) {
    if (await refreshAdminToken()) {
      return adminFetch<T>(path, init, false)
    }
  }

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ApiErrorBody | null
    throw new AdminApiError(
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
