import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { adminFetch, getAdminAccessToken, setAdminAccessToken } from '../api/http'

export interface AdminProfile {
  id: string
  email: string
  username: string
  displayName: string | null
  totpEnabled: boolean
  roles: string[]
  permissions: string[]
}

interface AdminLoginResponse {
  accessToken: string
  expiresIn: number
  totpEnabled: boolean
  admin: {
    id: string
    email: string
    username: string
    displayName: string | null
  }
  roles: string[]
  permissions: string[]
}

function adminDevice() {
  let deviceId = localStorage.getItem('boxue.admin.deviceId')
  if (!deviceId) {
    deviceId = crypto.randomUUID()
    localStorage.setItem('boxue.admin.deviceId', deviceId)
  }
  return {
    deviceId,
    deviceName: 'Admin Console',
    platform: 'ADMIN',
    appVersion: '0.1.0'
  }
}

export const useAdminAuthStore = defineStore('adminAuth', () => {
  const accessToken = ref<string | null>(getAdminAccessToken())
  const admin = ref<AdminProfile | null>(null)
  const restoring = ref(false)

  const isAuthenticated = computed(() => Boolean(accessToken.value && admin.value))

  function setToken(token: string | null) {
    accessToken.value = token
    setAdminAccessToken(token)
  }

  async function login(email: string, password: string, totpCode?: string) {
    const data = await adminFetch<AdminLoginResponse>('/admin/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        email,
        password,
        totpCode: totpCode || undefined,
        device: adminDevice()
      })
    })
    setToken(data.accessToken)
    admin.value = {
      ...data.admin,
      totpEnabled: data.totpEnabled,
      roles: data.roles,
      permissions: data.permissions
    }
  }

  async function restore() {
    if (admin.value) {
      return true
    }
    if (restoring.value) {
      return false
    }
    restoring.value = true
    try {
      const me = await adminFetch<AdminProfile>('/admin/auth/me')
      admin.value = me
      accessToken.value = getAdminAccessToken()
      return true
    } catch {
      setToken(null)
      admin.value = null
      return false
    } finally {
      restoring.value = false
    }
  }

  async function logout() {
    try {
      await adminFetch<void>('/admin/auth/logout', { method: 'POST' })
    } catch {
      // Clear local state even if the session is already gone.
    } finally {
      setToken(null)
      admin.value = null
    }
  }

  async function refreshProfile() {
    admin.value = await adminFetch<AdminProfile>('/admin/auth/me')
    return admin.value
  }

  function hasPermission(code: string) {
    return admin.value?.permissions.includes(code) ?? false
  }

  return {
    accessToken,
    admin,
    restoring,
    isAuthenticated,
    setToken,
    login,
    restore,
    refreshProfile,
    logout,
    hasPermission
  }
})
