import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface CurrentUser {
  id: string
  email: string
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(localStorage.getItem('boxue.accessToken'))
  const user = ref<CurrentUser | null>(null)

  function setToken(token: string | null) {
    accessToken.value = token
    if (token) {
      localStorage.setItem('boxue.accessToken', token)
    } else {
      localStorage.removeItem('boxue.accessToken')
    }
  }

  async function login(login: string, password: string) {
    // TODO: replace with POST /api/v1/auth/login
    if (!login || !password) {
      throw new Error('请输入账号和密码')
    }
    setToken('dev-placeholder-token')
    user.value = {
      id: 'dev-user',
      email: login,
      username: login,
      displayName: login,
      roles: ['USER'],
      permissions: ['USER_BASIC']
    }
  }

  function logout() {
    setToken(null)
    user.value = null
  }

  return { accessToken, user, setToken, login, logout }
})