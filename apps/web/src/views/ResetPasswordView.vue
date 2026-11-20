<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const success = ref(false)
const form = reactive({ newPassword: '', confirmPassword: '' })
const token = typeof route.query.token === 'string' ? route.query.token : ''

async function submit() {
  if (!token) {
    ElMessage.error('重置链接缺少 token')
    return
  }
  if (form.newPassword.length < 8) {
    ElMessage.warning('新密码至少 8 位')
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }

  loading.value = true
  try {
    await auth.resetPassword(token, form.newPassword)
    success.value = true
    ElMessage.success('密码重置成功，请重新登录')
    window.setTimeout(() => router.replace('/login'), 800)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重置失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-title">重置密码</div>
      <div class="auth-subtitle">设置一个新的登录密码</div>
      <el-alert
        v-if="!token"
        title="重置链接缺少 token，请重新打开邮件中的链接。"
        type="error"
        :closable="false"
      />
      <template v-else>
        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="新密码">
            <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              show-password
              autocomplete="new-password"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-button type="primary" :loading="loading" class="auth-submit" @click="submit">
            重置密码
          </el-button>
        </el-form>
        <div v-if="success" class="auth-message">密码已重置，即将返回登录页。</div>
      </template>
      <div class="auth-links">
        <router-link to="/login">返回登录</router-link>
      </div>
    </div>
  </div>
</template>
