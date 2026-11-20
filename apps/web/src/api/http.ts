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

let refreshPromise: Promise<boolean> | null = null

async function refreshAccessToken(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const response = await fetch(`${baseUrl}/auth/refresh`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          'X-Client-Platform': 'web'
        },
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
    })().finally(() => {
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
