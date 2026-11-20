<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(true)
const success = ref(false)
const message = ref('正在验证邮箱...')

onMounted(async () => {
  const token = typeof route.query.token === 'string' ? route.query.token : ''
  if (!token) {
    loading.value = false
    message.value = '验证链接缺少 token，请重新打开邮件中的链接。'
    return
  }

  try {
    await auth.verifyEmail(token)
    success.value = true
    message.value = '邮箱验证成功，正在进入学习概览...'
    ElMessage.success('邮箱验证成功')
    window.setTimeout(() => router.replace('/dashboard'), 800)
  } catch (error) {
    message.value = error instanceof Error ? error.message : '邮箱验证失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-title">邮箱验证</div>
      <div class="auth-subtitle">验证后即可正常登录</div>
      <div v-if="loading" class="auth-message">
        <el-skeleton :rows="2" animated />
      </div>
      <div v-else class="auth-message">
        <el-result
          :icon="success ? 'success' : 'error'"
          :title="success ? '验证成功' : '验证失败'"
          :sub-title="message"
        >
          <template #extra>
            <el-button v-if="success" type="primary" @click="router.replace('/dashboard')">
              进入学习概览
            </el-button>
            <router-link v-else to="/login">返回登录</router-link>
          </template>
        </el-result>
      </div>
    </div>
  </div>
</template>
