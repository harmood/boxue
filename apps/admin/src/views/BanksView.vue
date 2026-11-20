<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getBank, listBanks, type AdminBankItem } from '../api/banks'

const loading = ref(false)
const banks = ref<AdminBankItem[]>([])
const hasMore = ref(false)
const nextCursor = ref<string | null>(null)
const currentCursor = ref<string | null>(null)
const cursorStack = ref<(string | null)[]>([])
const pageIndex = ref(1)
const filters = reactive({ q: '', ownerId: '', status: '', visibility: '' })

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminBankItem | null>(null)

const pageLabel = computed(() => `第 ${pageIndex.value} 页`)

onMounted(() => load())

async function load(cursor: string | null = null) {
  loading.value = true
  try {
    const page = await listBanks({
      q: filters.q,
      ownerId: filters.ownerId,
      status: filters.status,
      visibility: filters.visibility,
      cursor,
      limit: 20
    })
    banks.value = page.items
    hasMore.value = page.hasMore
    nextCursor.value = page.nextCursor
    currentCursor.value = cursor
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载题库失败')
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
  filters.ownerId = ''
  filters.status = ''
  filters.visibility = ''
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

async function openDetail(row: AdminBankItem) {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getBank(row.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

function statusType(status: string) {
  return status === 'ACTIVE' ? 'success' : 'info'
}

function statusLabel(status: string) {
  return status === 'ACTIVE' ? '使用中' : '已归档'
}

function visibilityLabel(value: string) {
  const map: Record<string, string> = {
    PRIVATE: '私有',
    UNLISTED: '不公开链接',
    PUBLIC: '公开'
  }
  return map[value] ?? value
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString() : '-'
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h2>题库管理</h2>
        <p class="muted">只读查看全站题库、归属用户与题目数量，用于运营排查和内容治理。</p>
      </div>
    </div>

    <el-card shadow="never" class="filters">
      <el-form inline @submit.prevent="search">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.q"
            placeholder="题库名称或描述"
            clearable
            style="width: 200px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="所有者">
          <el-input
            v-model="filters.ownerId"
            placeholder="ownerId (UUID)"
            clearable
            style="width: 280px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="使用中" value="ACTIVE" />
            <el-option label="已归档" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="可见性">
          <el-select v-model="filters.visibility" placeholder="全部" clearable style="width: 140px">
            <el-option label="私有" value="PRIVATE" />
            <el-option label="不公开链接" value="UNLISTED" />
            <el-option label="公开" value="PUBLIC" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table v-loading="loading" :data="banks" border>
      <el-table-column prop="name" label="题库" min-width="180">
        <template #default="{ row }">
          <div class="bank-name">{{ row.name }}</div>
          <div class="bank-desc">{{ row.description || '暂无描述' }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="ownerEmail" label="所有者" min-width="200" />
      <el-table-column label="可见性" width="110">
        <template #default="{ row }">{{ visibilityLabel(row.visibility) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="questionCount" label="题目数" width="90" align="right" />
      <el-table-column label="最近更新" width="170">
        <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="没有匹配的题库" />
      </template>
    </el-table>

    <div class="pager">
      <span class="muted">{{ pageLabel }}</span>
      <el-button :disabled="cursorStack.length === 0" @click="prevPage">上一页</el-button>
      <el-button :disabled="!hasMore" @click="nextPage">下一页</el-button>
    </div>

    <el-drawer v-model="drawerVisible" title="题库详情" size="480px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="题库 ID">{{ detail.id }}</el-descriptions-item>
            <el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item>
            <el-descriptions-item label="描述">{{ detail.description || '-' }}</el-descriptions-item>
            <el-descriptions-item label="所有者">{{ detail.ownerEmail }}</el-descriptions-item>
            <el-descriptions-item label="所有者 ID">{{ detail.ownerId }}</el-descriptions-item>
            <el-descriptions-item label="可见性">{{ visibilityLabel(detail.visibility) }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
            <el-descriptions-item label="题目数">{{ detail.questionCount }}</el-descriptions-item>
            <el-descriptions-item label="版本">v{{ detail.version }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDate(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDate(detail.updatedAt) }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: flex-start; }
h2 { margin: 0 0 4px; }
.muted { margin: 0 0 16px; color: #64748b; }
.filters { margin-bottom: 16px; }
.filters :deep(.el-form-item) { margin-bottom: 0; }
.bank-name { font-weight: 600; }
.bank-desc { margin-top: 4px; color: #94a3b8; font-size: 12px; }
.pager { display: flex; justify-content: flex-end; align-items: center; gap: 12px; margin-top: 16px; }
.pager .muted { margin: 0; }
</style>
