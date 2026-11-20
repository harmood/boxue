<script setup lang="ts">
import { ref } from 'vue'

const users = ref([
  { id: 'demo', email: 'alice@example.com', username: 'alice', status: 'ACTIVE', roles: 'USER' }
])
</script>

<template>
  <div>
    <div class="head">
      <div><h2>用户管理</h2><p>查询用户、禁用账号、触发密码重置和分配角色。</p></div>
      <el-input placeholder="搜索邮箱或用户名" style="width: 260px" />
    </div>
    <el-table :data="users" border>
      <el-table-column prop="email" label="邮箱" min-width="220" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="status" label="状态" width="120" />
      <el-table-column prop="roles" label="角色" width="140" />
      <el-table-column label="操作" width="220">
        <template #default>
          <el-button size="small">详情</el-button>
          <el-button size="small" type="warning">禁用</el-button>
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