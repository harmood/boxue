<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const email = ref('')
const loading = ref(false)
const sent = ref(false)

async function submit() {
  if (!email.value) {
    ElMessage.warning('请输入邮箱')
    return
  }
  loading.value = true
  try {
    const result = await auth.forgotPassword(email.value)
    sent.value = true
    ElMessage.success(result.message)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发送失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-title">忘记密码</div>
      <div class="auth-subtitle">输入注册邮箱，我们会发送重置链接</div>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="邮箱">
          <el-input v-model="email" placeholder="alice@example.com" autocomplete="email" />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="auth-submit" @click="submit">
          发送重置邮件
        </el-button>
      </el-form>
      <div v-if="sent" class="auth-message">
        如果该邮箱存在，重置邮件已发送。请检查收件箱，必要时查看垃圾邮件。
      </div>
      <div class="auth-links">
        <router-link to="/login">返回登录</router-link>
      </div>
    </div>
  </div>
</template>
