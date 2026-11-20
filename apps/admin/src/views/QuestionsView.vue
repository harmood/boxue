<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getQuestion, listQuestions, type AdminQuestionItem } from '../api/questions'

const route = useRoute()
const loading = ref(false)
const questions = ref<AdminQuestionItem[]>([])
const hasMore = ref(false)
const nextCursor = ref<string | null>(null)
const currentCursor = ref<string | null>(null)
const cursorStack = ref<(string | null)[]>([])
const pageIndex = ref(1)
const filters = reactive({
  q: '',
  bankId: String(route.query.bankId ?? ''),
  ownerId: '',
  type: '',
  status: ''
})

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminQuestionItem | null>(null)

const pageLabel = computed(() => `第 ${pageIndex.value} 页`)

onMounted(() => load())

async function load(cursor: string | null = null) {
  loading.value = true
  try {
    const page = await listQuestions({
      q: filters.q,
      bankId: filters.bankId,
      ownerId: filters.ownerId,
      type: filters.type,
      status: filters.status,
      cursor,
      limit: 20
    })
    questions.value = page.items
    hasMore.value = page.hasMore
    nextCursor.value = page.nextCursor
    currentCursor.value = cursor
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载题目失败')
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
  filters.bankId = ''
  filters.ownerId = ''
  filters.type = ''
  filters.status = ''
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

async function openDetail(row: AdminQuestionItem) {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getQuestion(row.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

function typeLabel(type: string) {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选题',
    MULTIPLE_CHOICE: '多选题',
    TRUE_FALSE: '判断题',
    FILL_BLANK: '填空题'
  }
  return map[type] ?? type
}

function statusLabel(status: string) {
  const map: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }
  return map[status] ?? status
}

function statusTag(status: string) {
  const map: Record<string, string> = { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }
  return map[status] ?? 'info'
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString() : '-'
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h2>题目管理</h2>
        <p class="muted">只读查看全站题目内容与题型数据，用于内容审核和问题排查。</p>
      </div>
    </div>

    <el-card shadow="never" class="filters">
      <el-form inline @submit.prevent="search">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.q"
            placeholder="题干、解析或选项"
            clearable
            style="width: 200px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="题库">
          <el-input v-model="filters.bankId" placeholder="bankId (UUID)" clearable style="width: 260px" />
        </el-form-item>
        <el-form-item label="所有者">
          <el-input v-model="filters.ownerId" placeholder="ownerId (UUID)" clearable style="width: 260px" />
        </el-form-item>
        <el-form-item label="题型">
          <el-select v-model="filters.type" placeholder="全部" clearable style="width: 120px">
            <el-option label="单选题" value="SINGLE_CHOICE" />
            <el-option label="多选题" value="MULTIPLE_CHOICE" />
            <el-option label="判断题" value="TRUE_FALSE" />
            <el-option label="填空题" value="FILL_BLANK" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已归档" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table v-loading="loading" :data="questions" border>
      <el-table-column label="题型" width="100">
        <template #default="{ row }">{{ typeLabel(row.type) }}</template>
      </el-table-column>
      <el-table-column label="题干" min-width="300">
        <template #default="{ row }">
          <div class="stem">{{ row.stem?.content }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="bankName" label="题库" min-width="140" />
      <el-table-column prop="ownerEmail" label="所有者" min-width="190" />
      <el-table-column label="难度" width="70" align="center">
        <template #default="{ row }">{{ row.difficulty }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近更新" width="170">
        <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="没有匹配的题目" />
      </template>
    </el-table>

    <div class="pager">
      <span class="muted">{{ pageLabel }}</span>
      <el-button :disabled="cursorStack.length === 0" @click="prevPage">上一页</el-button>
      <el-button :disabled="!hasMore" @click="nextPage">下一页</el-button>
    </div>

    <el-drawer v-model="drawerVisible" title="题目详情" size="620px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="题目 ID">{{ detail.id }}</el-descriptions-item>
            <el-descriptions-item label="题库">{{ detail.bankName }}（{{ detail.bankId }}）</el-descriptions-item>
            <el-descriptions-item label="所有者">{{ detail.ownerEmail }}</el-descriptions-item>
            <el-descriptions-item label="题型">{{ typeLabel(detail.type) }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
            <el-descriptions-item label="难度">{{ detail.difficulty }}</el-descriptions-item>
            <el-descriptions-item label="题干">
              <div class="stem">{{ detail.stem?.content }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="解析">{{ detail.explanation?.content || '-' }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{ detail.source || '-' }}</el-descriptions-item>
            <el-descriptions-item label="版本">v{{ detail.version }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDate(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDate(detail.updatedAt) }}</el-descriptions-item>
          </el-descriptions>
          <h4>题型数据（payload）</h4>
          <pre class="payload">{{ JSON.stringify(detail.payload, null, 2) }}</pre>
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
.stem { white-space: pre-wrap; line-height: 1.5; }
.pager { display: flex; justify-content: flex-end; align-items: center; gap: 12px; margin-top: 16px; }
.pager .muted { margin: 0; }
.payload { padding: 12px; background: #0f172a; color: #e2e8f0; border-radius: 8px; overflow: auto; font-size: 12px; }
</style>
