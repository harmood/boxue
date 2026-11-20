<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useAdminAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAdminAuthStore()

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="shell">
    <el-aside width="220px" class="sidebar">
      <div class="brand"><span class="mark">博</span><span>博学管理后台</span></div>
      <el-menu :default-active="route.path" router>
        <el-menu-item index="/dashboard">系统概览</el-menu-item>
        <el-menu-item index="/users">用户管理</el-menu-item>
        <el-menu-item index="/roles">角色权限</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <span>开发与运营管理</span>
        <el-button text @click="logout">退出</el-button>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.shell { min-height: 100%; }
.sidebar { background: #0f172a; color: #fff; padding: 16px 0; }
.brand { display: flex; align-items: center; gap: 10px; padding: 8px 20px 24px; font-weight: 700; }
.mark { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 9px; background: #409eff; }
:deep(.el-menu) { background: transparent; border-right: none; }
:deep(.el-menu-item) { color: #cbd5e1; }
:deep(.el-menu-item.is-active) { color: #fff; background: #1e293b; }
.topbar { display: flex; align-items: center; justify-content: space-between; background: #fff; border-bottom: 1px solid #e5e7eb; }
</style>