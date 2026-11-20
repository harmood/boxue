import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAdminAuthStore = defineStore('adminAuth', () => {
  const accessToken = ref<string | null>(localStorage.getItem('boxue.admin.accessToken'))

  function setToken(token: string | null) {
    accessToken.value = token
    if (token) {
      localStorage.setItem('boxue.admin.accessToken', token)
    } else {
      localStorage.removeItem('boxue.admin.accessToken')
    }
  }

  async function login(email: string, password: string, totpCode: string) {
    // TODO: replace with POST /api/v1/admin/auth/login
    if (!email || !password) {
      throw new Error('请输入管理员账号和密码')
    }
    if (!totpCode) {
      throw new Error('请输入 2FA 验证码（开发占位）')
    }
    setToken('dev-admin-token')
  }

  function logout() {
    setToken(null)
  }

  return { accessToken, setToken, login, logout }
})