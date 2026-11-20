<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

onMounted(() => {
  if (!auth.user) {
    auth.loadMe().catch(() => undefined)
  }
})

async function handleCommand(command: string) {
  if (command === 'logout') {
    await auth.logout()
    router.push('/login')
  }
}
</script>

<template>
  <el-container class="shell">
    <el-aside width="220px" class="sidebar">
      <div class="brand">
        <span class="brand-mark">博</span>
        <span>博学</span>
      </div>
      <el-menu :default-active="route.path" router>
        <el-menu-item index="/dashboard">学习概览</el-menu-item>
        <el-menu-item index="/banks">我的题库</el-menu-item>
        <el-menu-item index="/practice">开始练习</el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div>
          <strong>博学</strong>
          <span class="muted">多端学习平台</span>
        </div>
        <el-dropdown @command="handleCommand">
          <span class="user-entry">
          {{ auth.user?.displayName || auth.user?.email || '未登录' }}
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.shell {
  min-height: 100%;
}
.sidebar {
  background: #111827;
  color: white;
  padding: 16px 0;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 20px 24px;
  font-size: 20px;
  font-weight: 700;
}
.brand-mark {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: #409eff;
}
:deep(.el-menu) {
  background: transparent;
  border-right: none;
}
:deep(.el-menu-item) {
  color: #cbd5e1;
}
:deep(.el-menu-item.is-active) {
  color: #fff;
  background: #1f2937;
}
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: white;
  border-bottom: 1px solid #e5e7eb;
}
.muted {
  margin-left: 10px;
  color: #94a3b8;
  font-size: 13px;
}
.user-entry {
  cursor: pointer;
  color: #334155;
}
</style>
