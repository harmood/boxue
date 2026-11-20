<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createBank,
  deleteBank,
  listBanks,
  updateBank,
  type BankStatus,
  type BankView,
  type BankVisibility
} from '../api/banks'
import { ApiRequestError } from '../api/http'

const router = useRouter()

const visibilityOptions: { label: string; value: BankVisibility }[] = [
  { label: '私有', value: 'PRIVATE' },
  { label: '不公开链接', value: 'UNLISTED' },
  { label: '公开', value: 'PUBLIC' }
]

const loading = ref(false)
const banks = ref<BankView[]>([])
const hasMore = ref(false)
const nextCursor = ref<string | null>(null)
const currentCursor = ref<string | null>(null)
const cursorStack = ref<(string | null)[]>([])
const pageIndex = ref(1)
const filters = reactive({ q: '', status: '' })

const createVisible = ref(false)
const createSaving = ref(false)
const createForm = reactive({
  name: '',
  description: '',
  visibility: 'PRIVATE' as BankVisibility
})

const editVisible = ref(false)
const editSaving = ref(false)
const editForm = reactive({
  id: '',
  name: '',
  description: '',
  status: 'ACTIVE' as BankStatus,
  version: 1
})

const pageLabel = computed(() => `第 ${pageIndex.value} 页`)

onMounted(() => load())

async function load(cursor: string | null = null) {
  loading.value = true
  try {
    const page = await listBanks({
      q: filters.q,
      status: filters.status,
      cursor,
      limit: 20
    })
    banks.value = page.items
    hasMore.value = page.hasMore
    nextCursor.value = page.nextCursor
    currentCursor.value = cursor
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载题库失败'))
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

function openCreate() {
  createForm.name = ''
  createForm.description = ''
  createForm.visibility = 'PRIVATE'
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.name.trim()) {
    ElMessage.warning('请输入题库名称')
    return
  }
  createSaving.value = true
  try {
    await createBank({
      name: createForm.name.trim(),
      description: createForm.description.trim() || null,
      visibility: createForm.visibility
    })
    ElMessage.success('题库已创建')
    createVisible.value = false
    search()
  } catch (error) {
    ElMessage.error(errorMessage(error, '创建题库失败'))
  } finally {
    createSaving.value = false
  }
}

function openEdit(row: BankView) {
  editForm.id = row.id
  editForm.name = row.name
  editForm.description = row.description ?? ''
  editForm.status = row.status
  editForm.version = row.version
  editVisible.value = true
}

async function submitEdit() {
  if (!editForm.name.trim()) {
    ElMessage.warning('请输入题库名称')
    return
  }
  editSaving.value = true
  try {
    await updateBank(editForm.id, {
      name: editForm.name.trim(),
      description: editForm.description.trim() || null,
      status: editForm.status,
      version: editForm.version
    })
    ElMessage.success('题库已更新')
    editVisible.value = false
    await load(currentCursor.value)
  } catch (error) {
    if (error instanceof ApiRequestError && error.code === 'BANK_VERSION_CONFLICT') {
      ElMessage.error('题库已被其他设备修改，请刷新后重试')
    } else {
      ElMessage.error(errorMessage(error, '更新题库失败'))
    }
  } finally {
    editSaving.value = false
  }
}

async function handleDelete(row: BankView) {
  try {
    await ElMessageBox.confirm(`确定删除题库「${row.name}」吗？`, '删除题库', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deleteBank(row.id)
    ElMessage.success('题库已删除')
    await load(currentCursor.value)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    if (error instanceof ApiRequestError && error.code === 'BANK_NOT_EMPTY') {
      await confirmCascadeDelete(row, Number(error.details?.questionCount ?? 0))
      return
    }
    ElMessage.error(errorMessage(error, '删除题库失败'))
  }
}

async function confirmCascadeDelete(row: BankView, questionCount: number) {
  try {
    await ElMessageBox.confirm(
      `题库「${row.name}」中仍有 ${questionCount} 道题目，删除后题目也会一并删除（可从同步历史恢复）。是否继续？`,
      '题库非空',
      { type: 'warning', confirmButtonText: '删除题库及题目', cancelButtonText: '取消' }
    )
    await deleteBank(row.id, true)
    ElMessage.success('题库及其题目已删除')
    await load(currentCursor.value)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(errorMessage(error, '删除题库失败'))
  }
}

function statusTag(status: string) {
  return status === 'ACTIVE' ? 'success' : 'info'
}

function statusLabel(status: string) {
  return status === 'ACTIVE' ? '使用中' : '已归档'
}

function visibilityLabel(value: string) {
  return visibilityOptions.find((item) => item.value === value)?.label ?? value
}

function formatDate(value: string) {
  return new Date(value).toLocaleString()
}

function formatAccuracy(value: number) {
  if (!value) return '--'
  return `${(value * 100).toFixed(1)}%`
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h2>我的题库</h2>
        <p class="muted">创建、维护和归档自己的题库，所有修改都会同步到多端。</p>
      </div>
      <el-button type="primary" @click="openCreate">新建题库</el-button>
    </div>

    <el-card shadow="never" class="filters">
      <el-form inline @submit.prevent="search">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.q"
            placeholder="题库名称或描述"
            clearable
            style="width: 220px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="使用中" value="ACTIVE" />
            <el-option label="已归档" value="ARCHIVED" />
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
      <el-table-column label="可见性" width="110">
        <template #default="{ row }">{{ visibilityLabel(row.visibility) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="questionCount" label="题目数" width="90" align="right" />
      <el-table-column label="正确率" width="100" align="right">
        <template #default="{ row }">{{ formatAccuracy(row.accuracy) }}</template>
      </el-table-column>
      <el-table-column label="最近更新" width="170">
        <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/banks/${row.id}/questions`)">题目</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="还没有题库，先创建一个吧" />
      </template>
    </el-table>

    <div class="pager">
      <span class="muted">{{ pageLabel }}</span>
      <el-button :disabled="cursorStack.length === 0" @click="prevPage">上一页</el-button>
      <el-button :disabled="!hasMore" @click="nextPage">下一页</el-button>
    </div>

    <el-dialog v-model="createVisible" title="新建题库" width="480px">
      <el-form label-width="80px" @submit.prevent="submitCreate">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" maxlength="200" show-word-limit placeholder="例如：高等数学" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            maxlength="5000"
            placeholder="简单说明题库内容（可选）"
          />
        </el-form-item>
        <el-form-item label="可见性">
          <el-select v-model="createForm.visibility" style="width: 100%">
            <el-option
              v-for="item in visibilityOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑题库" width="480px">
      <el-form label-width="80px" @submit.prevent="submitEdit">
        <el-form-item label="名称" required>
          <el-input v-model="editForm.name" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" maxlength="5000" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editForm.status">
            <el-radio value="ACTIVE">使用中</el-radio>
            <el-radio value="ARCHIVED">已归档</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="版本">
          <span class="muted">v{{ editForm.version }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
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
