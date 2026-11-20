const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

export async function adminFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('boxue.admin.accessToken')
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`${baseUrl}${path}`, { ...init, headers })
  if (!response.ok) {
    const error = await response.json().catch(() => null)
    throw new Error(error?.message ?? `请求失败：${response.status}`)
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}