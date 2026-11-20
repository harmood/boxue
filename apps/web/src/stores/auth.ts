import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { apiFetch, getAccessToken, setAccessToken } from '../api/http'

export interface CurrentUser {
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
}

interface TokenResponse {
  accessToken: string
  expiresIn: number
  refreshToken: string | null
  user: {
    id: string
    email: string
    username: string
    displayName: string | null
    status: string
    emailVerified: boolean
  }
  roles: string[]
  permissions: string[]
}

interface RegisterResponse {
  user: {
    id: string
    email: string
    username: string
    displayName: string | null
    status: string
    emailVerified: boolean
  }
  message: string
}

interface VerifyEmailResponse {
  user: {
    id: string
    email: string
    username: string
    displayName: string | null
    status: string
    emailVerified: boolean
  }
  accessToken: string
  expiresIn: number
  refreshToken: string | null
}

interface MessageResponse {
  message: string
}

function deviceInfo() {
  let deviceId = localStorage.getItem('boxue.deviceId')
  if (!deviceId) {
    deviceId = crypto.randomUUID()
    localStorage.setItem('boxue.deviceId', deviceId)
  }
  return {
    deviceId,
    deviceName: 'Web Browser',
    platform: 'WEB',
    appVersion: '0.1.0'
  }
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(getAccessToken())
  const user = ref<CurrentUser | null>(null)
  const restoring = ref(false)

  const isAuthenticated = computed(() => Boolean(accessToken.value && user.value))

  function setToken(token: string | null) {
    accessToken.value = token
    setAccessToken(token)
  }

  async function login(login: string, password: string) {
    const data = await apiFetch<TokenResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ login, password, device: deviceInfo() })
    })
    setToken(data.accessToken)
    await loadMe()
  }

  async function register(input: {
    email: string
    username: string
    password: string
    displayName?: string
    acceptedTerms: boolean
  }) {
    return apiFetch<RegisterResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(input)
    })
  }

  async function loadMe() {
    const me = await apiFetch<CurrentUser>('/me')
    user.value = me
    accessToken.value = getAccessToken()
    return me
  }

  async function restore() {
    if (user.value) {
      return true
    }
    if (restoring.value) {
      return false
    }
    restoring.value = true
    try {
      await loadMe()
      return true
    } catch {
      setToken(null)
      user.value = null
      return false
    } finally {
      restoring.value = false
    }
  }

  async function logout() {
    try {
      await apiFetch<void>('/auth/logout', { method: 'POST' })
    } catch {
      // Even if the server session is already gone, clear local state.
    } finally {
      setToken(null)
      user.value = null
    }
  }

  async function verifyEmail(token: string) {
    const data = await apiFetch<VerifyEmailResponse>('/auth/verify-email', {
      method: 'POST',
      body: JSON.stringify({ token })
    })
    if (data.accessToken) {
      setToken(data.accessToken)
      await loadMe().catch(() => undefined)
    }
    return data
  }

  async function resendVerification(email: string) {
    return apiFetch<MessageResponse>('/auth/resend-verification', {
      method: 'POST',
      body: JSON.stringify({ email })
    })
  }

  async function forgotPassword(email: string) {
    return apiFetch<MessageResponse>('/auth/forgot-password', {
      method: 'POST',
      body: JSON.stringify({ email })
    })
  }

  async function resetPassword(token: string, newPassword: string) {
    return apiFetch<void>('/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({ token, newPassword })
    })
  }

  return {
    accessToken,
    user,
    restoring,
    isAuthenticated,
    setToken,
    login,
    register,
    loadMe,
    restore,
    logout,
    verifyEmail,
    resendVerification,
    forgotPassword,
    resetPassword
  }
})
