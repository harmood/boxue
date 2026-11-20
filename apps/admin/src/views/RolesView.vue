<script setup lang="ts">
import { ref } from 'vue'

const roles = ref([
  { code: 'USER', name: '普通用户', permissions: 'USER_BASIC', system: true },
  { code: 'ADMIN', name: '管理员', permissions: 'ADMIN_*', system: true }
])
</script>

<template>
  <div>
    <div class="head">
      <div><h2>角色权限</h2><p>基于 RBAC 管理角色和权限码。</p></div>
      <el-button type="primary">新建角色</el-button>
    </div>
    <el-table :data="roles" border>
      <el-table-column prop="code" label="角色码" width="160" />
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column prop="permissions" label="权限" min-width="260" />
      <el-table-column prop="system" label="系统角色" width="110" />
      <el-table-column label="操作" width="160">
        <template #default>
          <el-button size="small">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: flex-start; }
h2 { margin: 0 0 4px; }
p { margin: 0 0 16px; color: #64748b; }
</style>