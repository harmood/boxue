<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ login: '', password: '' })

async function submit() {
  loading.value = true
  try {
    await auth.login(form.login, form.password)
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
      <div class="title">博学</div>
      <div class="subtitle">把知识变成自己的题库</div>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="邮箱或用户名">
          <el-input v-model="form.login" placeholder="alice@example.com" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="submit" @click="submit">
          登录
        </el-button>
      </el-form>
      <div class="hint">当前为骨架页面，登录接口将在后端接入后替换。</div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100%;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #eef2ff, #f8fafc 55%, #ecfeff);
}
.login-card {
  width: min(420px, calc(100vw - 32px));
  padding: 32px;
  border-radius: 18px;
  background: white;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.12);
}
.title {
  font-size: 30px;
  font-weight: 800;
  color: #0f172a;
}
.subtitle {
  margin: 6px 0 24px;
  color: #64748b;
}
.submit {
  width: 100%;
  margin-top: 8px;
}
.hint {
  margin-top: 16px;
  font-size: 12px;
  color: #94a3b8;
}
</style>