<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAdminAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAdminAuthStore()
</script>

<template>
  <div>
    <h2>系统概览</h2>
    <el-alert
      v-if="auth.admin && !auth.admin.totpEnabled"
      type="warning"
      :closable="false"
      title="管理员账号尚未启用 2FA"
      description="建议立即启用 TOTP 双因素认证，保护管理后台登录安全。"
      show-icon
      class="security-alert"
    >
      <el-button type="warning" size="small" @click="router.push('/security')">去启用</el-button>
    </el-alert>
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="6"><el-card><div class="label">用户</div><div class="value">0</div></el-card></el-col>
      <el-col :xs="24" :sm="12" :lg="6"><el-card><div class="label">题库</div><div class="value">0</div></el-card></el-col>
      <el-col :xs="24" :sm="12" :lg="6"><el-card><div class="label">同步成功率</div><div class="value">--</div></el-card></el-col>
      <el-col :xs="24" :sm="12" :lg="6"><el-card><div class="label">API P95</div><div class="value">--</div></el-card></el-col>
    </el-row>
    <el-card class="panel">
      <template #header>监控接入</template>
      <p>后端接入 Spring Boot Actuator 和 Prometheus 后，这里将展示同步、任务队列、数据库和存储指标。</p>
    </el-card>
  </div>
</template>

<style scoped>
h2 { margin-top: 0; }
.security-alert { margin-bottom: 16px; }
.label { color: #64748b; font-size: 13px; }
.value { margin-top: 8px; font-size: 30px; font-weight: 800; }
.panel { margin-top: 16px; }
</style>
