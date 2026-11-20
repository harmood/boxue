<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAdminAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAdminAuthStore()
const loading = ref(false)
const form = reactive({ email: '', password: '', totpCode: '' })

async function submit() {
  loading.value = true
  try {
    await auth.login(form.email, form.password, form.totpCode)
    ElMessage.success('登录成功')
    router.push('/dashboard')
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
      <el-form label-position="top">
        <el-form-item label="管理员邮箱">
          <el-input v-model="form.email" placeholder="admin@example.com" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="2FA 验证码">
          <el-input v-model="form.totpCode" maxlength="6" placeholder="123456" />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="submit" @click="submit">登录</el-button>
      </el-form>
      <div class="hint">当前为骨架页面，管理端登录接口将在后端接入后替换。</div>
    </div>
  </div>
</template>

<style scoped>
.login-page { min-height: 100%; display: grid; place-items: center; background: #0f172a; }
.login-card { width: min(420px, calc(100vw - 32px)); padding: 32px; border-radius: 18px; background: #fff; box-shadow: 0 20px 60px rgba(0,0,0,.3); }
.title { font-size: 26px; font-weight: 800; }
.subtitle { margin: 6px 0 24px; color: #64748b; }
.submit { width: 100%; }
.hint { margin-top: 16px; font-size: 12px; color: #94a3b8; }
</style>