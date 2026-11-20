<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  forceLogout,
  getUser,
  listRoles,
  listUsers,
  setUserRoles,
  triggerPasswordReset,
  updateUserStatus,
  type AdminUserDetail,
  type AdminUserListItem,
  type Role
} from '../api/admin'

const loading = ref(false)
const users = ref<AdminUserListItem[]>([])
const hasMore = ref(false)
const nextCursor = ref<string | null>(null)
const currentCursor = ref<string | null>(null)
const cursorStack = ref<(string | null)[]>([])
const pageIndex = ref(1)
const filters = reactive({ q: '', status: '', role: '' })

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminUserDetail | null>(null)

const rolesDialogVisible = ref(false)
const rolesLoading = ref(false)
const allRoles = ref<Role[]>([])
const selectedRoleCodes = ref<string[]>([])
const roleTarget = ref<AdminUserDetail | null>(null)
const savingRoles = ref(false)

const pageLabel = computed(() => `第 ${pageIndex.value} 页`)

onMounted(() => load())

async function load(cursor: string | null = null) {
  loading.value = true
  try {
    const page = await listUsers({
      q: filters.q,
      status: filters.status,
      role: filters.role,
      cursor,
      limit: 20
    })
    users.value = page.items
    hasMore.value = page.hasMore
    nextCursor.value = page.nextCursor
    currentCursor.value = cursor
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载用户失败')
  } finally {
    loading.value = false
  }
}

function search() {
  cursorStack.value = []
  pageIndex.value = 1
  currentCursor.value = null
  load(null)
}

function resetFilters() {
  filters.q = ''
  filters.status = ''
  filters.role = ''
  search()
}

function nextPage() {
  if (!hasMore.value || !nextCursor.value) return
  cursorStack.value.push(currentCursor.value)
  pageIndex.value += 1
  load(nextCursor.value)
}

function prevPage() {
  if (cursorStack.value.length === 0) return
  cursorStack.value.pop()
  pageIndex.value = Math.max(1, pageIndex.value - 1)
  load(cursorStack.value[cursorStack.value.length - 1] ?? null)
}

function statusType(status: string) {
  const map: Record<string, string> = {
    ACTIVE: 'success',
    DISABLED: 'danger',
    LOCKED: 'warning',
    PENDING_VERIFICATION: 'info',
    DELETED: 'info'
  }
  return map[status] ?? 'info'
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString() : '-'
}

async function openDetail(row: AdminUserListItem) {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getUser(row.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function changeStatus(row: AdminUserListItem, status: string) {
  const action = status === 'ACTIVE' ? '启用' : status === 'DISABLED' ? '禁用' : '锁定'
  try {
    await ElMessageBox.confirm(`确定要${action}用户 ${row.email} 吗？`, '修改用户状态', {
      type: 'warning'
    })
    await updateUserStatus(row.id, status)
    ElMessage.success(`已${action}`)
    await load(currentCursor.value)
    if (detail.value?.id === row.id) {
      detail.value = await getUser(row.id)
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '修改状态失败')
    }
  }
}

async function openRoles(row: AdminUserListItem) {
  rolesDialogVisible.value = true
  rolesLoading.value = true
  try {
    if (allRoles.value.length === 0) {
      allRoles.value = (await listRoles()).items
    }
    roleTarget.value = await getUser(row.id)
    selectedRoleCodes.value = [...roleTarget.value.roles]
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载角色失败')
  } finally {
    rolesLoading.value = false
  }
}

async function saveRoles() {
  if (!roleTarget.value) return
  if (selectedRoleCodes.value.length === 0) {
    ElMessage.warning('用户至少需要一个角色')
    return
  }
  savingRoles.value = true
  try {
    await setUserRoles(roleTarget.value.id, selectedRoleCodes.value)
    ElMessage.success('角色已更新')
    rolesDialogVisible.value = false
    await load(currentCursor.value)
    if (detail.value?.id === roleTarget.value.id) {
      detail.value = await getUser(roleTarget.value.id)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存角色失败')
  } finally {
    savingRoles.value = false
  }
}

async function handleResetPassword(row: AdminUserListItem) {
  try {
    await ElMessageBox.confirm(`确定向 ${row.email} 发送密码重置邮件吗？`, '触发密码重置', {
      type: 'warning'
    })
    const result = await triggerPasswordReset(row.id)
    ElMessage.success(result.message)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '发送失败')
    }
  }
}

async function handleForceLogout(row: AdminUserListItem) {
  try {
    await ElMessageBox.confirm(`确定强制 ${row.email} 下线吗？`, '强制下线', {
      type: 'warning'
    })
    await forceLogout(row.id)
    ElMessage.success('已强制下线')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '操作失败')
    }
  }
}

function handleCommand(command: string, row: AdminUserListItem) {
  if (command === 'roles') return openRoles(row)
  if (command === 'reset-password') return handleResetPassword(row)
  if (command === 'force-logout') return handleForceLogout(row)
  return changeStatus(row, command)
}
</script>

<template>
  <div>
    <div class="head">
      <div>
        <h2>用户管理</h2>
        <p>查询用户、修改状态、分配角色、触发密码重置和强制下线。</p>
      </div>
    </div>

    <el-form inline class="filters">
      <el-form-item label="搜索">
        <el-input v-model="filters.q" placeholder="邮箱 / 用户名 / 昵称" clearable @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filters.status" placeholder="全部" clearable style="width: 150px">
          <el-option label="ACTIVE" value="ACTIVE" />
          <el-option label="DISABLED" value="DISABLED" />
          <el-option label="LOCKED" value="LOCKED" />
          <el-option label="PENDING_VERIFICATION" value="PENDING_VERIFICATION" />
        </el-select>
      </el-form-item>
      <el-form-item label="角色">
        <el-input v-model="filters.role" placeholder="USER / ADMIN" clearable style="width: 150px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="users" border stripe>
      <el-table-column prop="email" label="邮箱" min-width="220" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="displayName" label="昵称" width="140" />
      <el-table-column label="状态" width="150">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="180">
        <template #default="{ row }">
          <el-tag v-for="role in row.roles" :key="role" size="small" class="role-tag">{{ role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="邮箱验证" width="100">
        <template #default="{ row }">{{ row.emailVerified ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="最后登录" width="180">
        <template #default="{ row }">{{ formatDate(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDetail(row)">详情</el-button>
          <el-dropdown trigger="click" @command="(command: string) => handleCommand(command, row)">
            <el-button size="small" type="primary">更多</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="ACTIVE">启用</el-dropdown-item>
                <el-dropdown-item command="DISABLED">禁用</el-dropdown-item>
                <el-dropdown-item command="LOCKED">锁定</el-dropdown-item>
                <el-dropdown-item command="roles" divided>分配角色</el-dropdown-item>
                <el-dropdown-item command="reset-password">触发密码重置</el-dropdown-item>
                <el-dropdown-item command="force-logout">强制下线</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-button :disabled="cursorStack.length === 0" @click="prevPage">上一页</el-button>
      <span>{{ pageLabel }}</span>
      <el-button :disabled="!hasMore" @click="nextPage">下一页</el-button>
    </div>

    <el-drawer v-model="drawerVisible" title="用户详情" size="560px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ detail.email }}</el-descriptions-item>
            <el-descriptions-item label="用户名">{{ detail.username }}</el-descriptions-item>
            <el-descriptions-item label="昵称">{{ detail.displayName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusType(detail.status)">{{ detail.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="邮箱验证">{{ detail.emailVerified ? '是' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ formatDate(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="最后登录">{{ formatDate(detail.lastLoginAt) }}</el-descriptions-item>
            <el-descriptions-item label="题库 / 题目">{{ detail.bankCount }} / {{ detail.questionCount }}</el-descriptions-item>
            <el-descriptions-item label="存储用量">{{ (detail.storageBytes / 1024 / 1024).toFixed(2) }} MB</el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left">角色</el-divider>
          <el-tag v-for="role in detail.roles" :key="role" class="role-tag">{{ role }}</el-tag>

          <el-divider content-position="left">权限</el-divider>
          <div class="permission-list">
            <el-tag v-for="permission in detail.permissions" :key="permission" size="small" type="info">
              {{ permission }}
            </el-tag>
          </div>

          <el-divider content-position="left">设备</el-divider>
          <el-table :data="detail.devices" size="small" border>
            <el-table-column prop="platform" label="平台" width="100" />
            <el-table-column prop="deviceName" label="设备" min-width="150" />
            <el-table-column prop="appVersion" label="版本" width="90" />
            <el-table-column label="最后在线" width="170">
              <template #default="{ row }">{{ formatDate(row.lastSeenAt) }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="rolesDialogVisible" title="分配角色" width="520px">
      <div v-loading="rolesLoading">
        <p class="dialog-hint">角色会整体替换，用户至少需要一个角色。</p>
        <el-checkbox-group v-model="selectedRoleCodes">
          <el-checkbox v-for="role in allRoles" :key="role.code" :label="role.code" class="role-option">
            {{ role.name }}（{{ role.code }}）
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="rolesDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRoles" @click="saveRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head h2 { margin: 0 0 4px; }
.head p { margin: 0 0 16px; color: #64748b; }
.filters { margin-bottom: 8px; }
.role-tag { margin: 0 6px 6px 0; }
.permission-list { display: flex; flex-wrap: wrap; gap: 6px; }
.pager { display: flex; align-items: center; gap: 12px; margin-top: 16px; }
.dialog-hint { color: #64748b; font-size: 13px; }
.role-option { display: block; margin-bottom: 8px; }
</style>
