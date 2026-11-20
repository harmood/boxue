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

let refreshPromise: Promise<boolean> | null = null

async function refreshAdminToken(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const response = await fetch(`${baseUrl}/admin/auth/refresh`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          'X-Client-Platform': 'admin'
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
      setAdminAccessToken(data.accessToken)
      return true
    })().finally(() => {
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
