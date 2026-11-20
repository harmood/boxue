<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({
  email: '',
  username: '',
  displayName: '',
  password: '',
  confirmPassword: '',
  acceptedTerms: false
})

async function submit() {
  if (!form.email || !form.username || !form.password) {
    ElMessage.warning('请填写邮箱、用户名和密码')
    return
  }
  if (form.password.length < 8) {
    ElMessage.warning('密码至少 8 位')
    return
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (!form.acceptedTerms) {
    ElMessage.warning('请先同意用户协议')
    return
  }

  loading.value = true
  try {
    const result = await auth.register({
      email: form.email,
      username: form.username,
      password: form.password,
      displayName: form.displayName || undefined,
      acceptedTerms: true
    })
    ElMessage.success(result.message)
    router.push('/login')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-title">创建账号</div>
      <div class="auth-subtitle">注册后即可创建题库、练习和同步</div>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="alice@example.com" autocomplete="email" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="alice" autocomplete="username" />
        </el-form-item>
        <el-form-item label="昵称（可选）">
          <el-input v-model="form.displayName" placeholder="Alice" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            autocomplete="new-password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-checkbox v-model="form.acceptedTerms">我已阅读并同意用户协议</el-checkbox>
        <el-button type="primary" :loading="loading" class="auth-submit" @click="submit">
          注册
        </el-button>
      </el-form>
      <div class="auth-links">
        <router-link to="/login">已有账号，去登录</router-link>
      </div>
    </div>
  </div>
</template>
