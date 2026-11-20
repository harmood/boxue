<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAdminAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAdminAuthStore()
const loading = ref(false)
const form = reactive({ email: '', password: '', totpCode: '' })

async function submit() {
  if (!form.email || !form.password) {
    ElMessage.warning('请输入管理员邮箱和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.email, form.password, form.totpCode)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="title">博学管理后台</div>
      <div class="subtitle">仅限开发者和运营人员</div>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="管理员邮箱">
          <el-input v-model="form.email" placeholder="admin@boxue.local" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-form-item label="2FA 验证码（可选）">
          <el-input v-model="form.totpCode" maxlength="6" placeholder="当前未强制启用" />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="submit" @click="submit">登录</el-button>
      </el-form>
      <div class="hint">管理端使用独立 Token Audience，普通用户账号无法登录。</div>
    </div>
  </div>
</template>

<style scoped>
.login-page { min-height: 100%; display: grid; place-items: center; background: #0f172a; }
.login-card { width: min(420px, calc(100vw - 32px)); padding: 32px; border-radius: 18px; background: #fff; box-shadow: 0 20px 60px rgba(0,0,0,.3); }
.title { font-size: 26px; font-weight: 800; }
.subtitle { margin: 6px 0 24px; color: #64748b; }
.submit { width: 100%; }
.hint { margin-top: 16px; font-size: 12px; color: #94a3b8; line-height: 1.5; }
</style>
