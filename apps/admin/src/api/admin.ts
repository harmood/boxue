import { adminFetch } from './http'

export interface AdminUserListItem {
  id: string
  email: string
  username: string
  displayName: string | null
  status: string
  roles: string[]
  emailVerified: boolean
  lastLoginAt: string | null
  createdAt: string
}

export interface AdminUserPage {
  items: AdminUserListItem[]
  nextCursor: string | null
  hasMore: boolean
}

export interface AdminDeviceItem {
  id: string
  deviceName: string | null
  platform: string
  appVersion: string | null
  lastSeenAt: string | null
}

export interface AdminUserDetail {
  id: string
  email: string
  username: string
  displayName: string | null
  avatarMediaId: string | null
  bio: string | null
  locale: string
  timezone: string
  status: string
  roles: string[]
  permissions: string[]
  emailVerified: boolean
  createdAt: string
  lastLoginAt: string | null
  devices: AdminDeviceItem[]
  bankCount: number
  questionCount: number
  storageBytes: number
}

export interface PermissionRef {
  code: string
  name: string
}

export interface Role {
  id: string
  code: string
  name: string
  description: string | null
  isSystem: boolean
  permissions: PermissionRef[]
}

export interface Permission {
  id: string
  code: string
  name: string
  description: string | null
}

export interface AuditLogItem {
  id: number
  actorUserId: string | null
  action: string
  targetType: string | null
  targetId: string | null
  details: Record<string, unknown>
  ip: string | null
  userAgent: string | null
  createdAt: string
}

export interface AuditLogPage {
  items: AuditLogItem[]
  nextCursor: string | null
  hasMore: boolean
}

function queryString(params: Record<string, string | number | undefined | null>) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  })
  const text = search.toString()
  return text ? `?${text}` : ''
}

export function listUsers(params: {
  q?: string
  status?: string
  role?: string
  createdFrom?: string
  createdTo?: string
  cursor?: string | null
  limit?: number
}) {
  return adminFetch<AdminUserPage>(`/admin/users${queryString(params)}`)
}

export function getUser(userId: string) {
  return adminFetch<AdminUserDetail>(`/admin/users/${userId}`)
}

export function updateUserStatus(userId: string, status: string, reason?: string) {
  return adminFetch<AdminUserDetail>(`/admin/users/${userId}`, {
    method: 'PATCH',
    body: JSON.stringify({ status, reason })
  })
}

export function setUserRoles(userId: string, roleCodes: string[]) {
  return adminFetch<AdminUserDetail>(`/admin/users/${userId}/roles`, {
    method: 'PUT',
    body: JSON.stringify({ roleCodes })
  })
}

export function triggerPasswordReset(userId: string) {
  return adminFetch<{ message: string }>(`/admin/users/${userId}/password-reset`, {
    method: 'POST',
    body: '{}'
  })
}

export function forceLogout(userId: string) {
  return adminFetch<void>(`/admin/users/${userId}/force-logout`, {
    method: 'POST',
    body: '{}'
  })
}

export function listRoles() {
  return adminFetch<{ items: Role[] }>('/admin/roles')
}

export function listPermissions() {
  return adminFetch<{ items: Permission[] }>('/admin/permissions')
}

export function createRole(input: {
  code: string
  name: string
  description?: string
  permissionCodes: string[]
}) {
  return adminFetch<Role>('/admin/roles', {
    method: 'POST',
    body: JSON.stringify(input)
  })
}

export function updateRole(roleId: string, input: {
  name: string
  description?: string
  permissionCodes: string[]
}) {
  return adminFetch<Role>(`/admin/roles/${roleId}`, {
    method: 'PUT',
    body: JSON.stringify(input)
  })
}

export function deleteRole(roleId: string) {
  return adminFetch<void>(`/admin/roles/${roleId}`, { method: 'DELETE' })
}

export function listAuditLogs(params: {
  actorId?: string
  action?: string
  targetType?: string
  from?: string
  to?: string
  cursor?: string | null
  limit?: number
}) {
  return adminFetch<AuditLogPage>(`/admin/audit-logs${queryString(params)}`)
}
