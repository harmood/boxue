<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listAuditLogs, type AuditLogItem } from '../api/admin'

const loading = ref(false)
const logs = ref<AuditLogItem[]>([])
const hasMore = ref(false)
const nextCursor = ref<string | null>(null)
const currentCursor = ref<string | null>(null)
const cursorStack = ref<(string | null)[]>([])
const pageIndex = ref(1)
const filters = reactive({ actorId: '', action: '', targetType: '' })

onMounted(() => load())

async function load(cursor: string | null = null) {
  loading.value = true
  try {
    const page = await listAuditLogs({
      actorId: filters.actorId || undefined,
      action: filters.action || undefined,
      targetType: filters.targetType || undefined,
      cursor,
      limit: 30
    })
    logs.value = page.items
    hasMore.value = page.hasMore
    nextCursor.value = page.nextCursor
    currentCursor.value = cursor
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载审计日志失败')
  } finally {
    loading.value = false
  }
}

function search() {
  cursorStack.value = []
  pageIndex.value = 1
  load(null)
}

function resetFilters() {
  filters.actorId = ''
  filters.action = ''
  filters.targetType = ''
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

function formatDate(value: string) {
  return new Date(value).toLocaleString()
}

function formatDetails(details: Record<string, unknown>) {
  if (!details || Object.keys(details).length === 0) return '-'
  return JSON.stringify(details)
}
</script>

<template>
  <div>
    <div class="head">
      <div>
        <h2>审计日志</h2>
        <p>查询管理员和系统关键操作，用于追踪权限变更、状态变更和登录行为。</p>
      </div>
    </div>

    <el-form inline class="filters">
      <el-form-item label="操作人 ID">
        <el-input v-model="filters.actorId" placeholder="UUID" clearable style="width: 280px" />
      </el-form-item>
      <el-form-item label="动作">
        <el-input v-model="filters.action" placeholder="ADMIN_USER_STATUS_CHANGED" clearable style="width: 250px" />
      </el-form-item>
      <el-form-item label="目标类型">
        <el-input v-model="filters.targetType" placeholder="USER / ROLE / SESSION" clearable style="width: 180px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="logs" border stripe>
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="action" label="动作" width="240" />
      <el-table-column label="操作人" width="280">
        <template #default="{ row }">{{ row.actorUserId || '-' }}</template>
      </el-table-column>
      <el-table-column label="目标" min-width="260">
        <template #default="{ row }">
          <span>{{ row.targetType || '-' }}</span>
          <span v-if="row.targetId"> / {{ row.targetId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="详情" min-width="320">
        <template #default="{ row }">
          <span class="details">{{ formatDetails(row.details) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="IP" width="140">
        <template #default="{ row }">{{ row.ip || '-' }}</template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-button :disabled="cursorStack.length === 0" @click="prevPage">上一页</el-button>
      <span>第 {{ pageIndex }} 页</span>
      <el-button :disabled="!hasMore" @click="nextPage">下一页</el-button>
    </div>
  </div>
</template>

<style scoped>
.head h2 { margin: 0 0 4px; }
.head p { margin: 0 0 16px; color: #64748b; }
.filters { margin-bottom: 8px; }
.details { color: #475569; font-family: Consolas, monospace; font-size: 12px; word-break: break-all; }
.pager { display: flex; align-items: center; gap: 12px; margin-top: 16px; }
</style>
