<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBank, type BankView } from '../api/banks'
import MarkdownContent from '../components/MarkdownContent.vue'
import {
  archiveQuestion,
  deleteQuestion,
  listQuestions,
  publishQuestion,
  type QuestionType,
  type QuestionView
} from '../api/questions'

const route = useRoute()
const router = useRouter()
const bankId = String(route.params.bankId)

const typeOptions: { label: string; value: QuestionType }[] = [
  { label: '单选题', value: 'SINGLE_CHOICE' },
  { label: '多选题', value: 'MULTIPLE_CHOICE' },
  { label: '判断题', value: 'TRUE_FALSE' },
  { label: '填空题', value: 'FILL_BLANK' }
]

const bank = ref<BankView | null>(null)
const loading = ref(false)
const questions = ref<QuestionView[]>([])
const hasMore = ref(false)
const nextCursor = ref<string | null>(null)
const currentCursor = ref<string | null>(null)
const cursorStack = ref<(string | null)[]>([])
const pageIndex = ref(1)
const filters = reactive({ q: '', type: '', status: '' })

const pageLabel = computed(() => `第 ${pageIndex.value} 页`)

onMounted(async () => {
  await loadBank()
  await load()
})

async function loadBank() {
  try {
    bank.value = await getBank(bankId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载题库失败')
    router.push('/banks')
  }
}

async function load(cursor: string | null = null) {
  loading.value = true
  try {
    const page = await listQuestions(bankId, {
      q: filters.q,
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

function createQuestion() {
  router.push(`/banks/${bankId}/questions/new`)
}

function editQuestion(row: QuestionView) {
  router.push(`/banks/${bankId}/questions/${row.id}/edit`)
}

async function handlePublish(row: QuestionView) {
  try {
    await ElMessageBox.confirm(`确定发布这道题目吗？`, '发布题目', { type: 'warning' })
    await publishQuestion(row.id)
    ElMessage.success('题目已发布')
    await load(currentCursor.value)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '发布失败')
    }
  }
}

async function handleArchive(row: QuestionView) {
  try {
    await ElMessageBox.confirm(`确定归档这道题目吗？`, '归档题目', { type: 'warning' })
    await archiveQuestion(row.id)
    ElMessage.success('题目已归档')
    await load(currentCursor.value)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '归档失败')
    }
  }
}

async function handleDelete(row: QuestionView) {
  try {
    await ElMessageBox.confirm('确定删除这道题目吗？删除后可从同步历史恢复。', '删除题目', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deleteQuestion(row.id)
    ElMessage.success('题目已删除')
    await load(currentCursor.value)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除失败')
    }
  }
}

function typeLabel(type: string) {
  return typeOptions.find((item) => item.value === type)?.label ?? type
}

function typeTag(type: string) {
  const map: Record<string, string> = {
    SINGLE_CHOICE: 'primary',
    MULTIPLE_CHOICE: 'success',
    TRUE_FALSE: 'warning',
    FILL_BLANK: 'info'
  }
  return map[type] ?? 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }
  return map[status] ?? status
}

function statusTag(status: string) {
  const map: Record<string, string> = { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }
  return map[status] ?? 'info'
}

function formatDate(value: string) {
  return new Date(value).toLocaleString()
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <el-button text @click="router.push('/banks')">← 返回题库</el-button>
        <h2>{{ bank?.name || '题目管理' }}</h2>
        <p class="muted">维护单选题、多选题、判断题和填空题，修改会记录版本并同步到多端。</p>
      </div>
      <el-button type="primary" @click="createQuestion">新建题目</el-button>
    </div>

    <el-card shadow="never" class="filters">
      <el-form inline @submit.prevent="search">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.q"
            placeholder="题干、解析或选项"
            clearable
            style="width: 220px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="题型">
          <el-select v-model="filters.type" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 130px">
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
        <template #default="{ row }">
          <el-tag :type="typeTag(row.type)" size="small">{{ typeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="题干" min-width="320">
        <template #default="{ row }">
          <MarkdownContent :content="row.stem.content" class="stem" />
        </template>
      </el-table-column>
      <el-table-column label="难度" width="80" align="center">
        <template #default="{ row }">{{ row.difficulty }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="版本" width="70" align="center">
        <template #default="{ row }">v{{ row.version }}</template>
      </el-table-column>
      <el-table-column label="最近更新" width="170">
        <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="editQuestion(row)">编辑</el-button>
          <el-button v-if="row.status !== 'PUBLISHED'" link type="success" @click="handlePublish(row)">
            发布
          </el-button>
          <el-button v-if="row.status !== 'ARCHIVED'" link type="warning" @click="handleArchive(row)">
            归档
          </el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="还没有题目，先创建一道吧" />
      </template>
    </el-table>

    <div class="pager">
      <span class="muted">{{ pageLabel }}</span>
      <el-button :disabled="cursorStack.length === 0" @click="prevPage">上一页</el-button>
      <el-button :disabled="!hasMore" @click="nextPage">下一页</el-button>
    </div>
  </div>
</template>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: flex-start; }
h2 { margin: 4px 0; }
.muted { margin: 0 0 16px; color: #64748b; }
.filters { margin-bottom: 16px; }
.filters :deep(.el-form-item) { margin-bottom: 0; }
.stem { line-height: 1.5; max-height: 140px; overflow: hidden; }
.pager { display: flex; justify-content: flex-end; align-items: center; gap: 12px; margin-top: 16px; }
.pager .muted { margin: 0; }
</style>
