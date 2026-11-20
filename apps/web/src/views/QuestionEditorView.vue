<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { getBank } from '../api/banks'
import { uploadMedia } from '../api/media'
import MarkdownContent from '../components/MarkdownContent.vue'
import MediaImage from '../components/MediaImage.vue'
import {
  createQuestion,
  getQuestion,
  updateQuestion,
  type ChoicePayload,
  type CreateQuestionInput,
  type FillBlankPayload,
  type QuestionStatus,
  type QuestionType,
  type TrueFalsePayload
} from '../api/questions'

interface OptionForm {
  key: string
  content: string
}

interface BlankForm {
  id: string
  answers: string
  caseSensitive: boolean
  trimWhitespace: boolean
}

const route = useRoute()
const router = useRouter()
const bankId = String(route.params.bankId)
const questionId = route.params.questionId ? String(route.params.questionId) : null
const isEdit = computed(() => Boolean(questionId))

const typeOptions: { label: string; value: QuestionType; hint: string }[] = [
  { label: '单选题', value: 'SINGLE_CHOICE', hint: '多个选项中只有一个正确答案' },
  { label: '多选题', value: 'MULTIPLE_CHOICE', hint: '多个选项中可有一个或多个正确答案' },
  { label: '判断题', value: 'TRUE_FALSE', hint: '答案为正确或错误' },
  { label: '填空题', value: 'FILL_BLANK', hint: '题干用 ____ 表示空位，可配置多个可接受答案' }
]

const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const bankName = ref('')

const form = reactive({
  type: 'SINGLE_CHOICE' as QuestionType,
  status: 'DRAFT' as QuestionStatus,
  stem: '',
  explanation: '',
  difficulty: 3,
  source: '',
  mediaIds: [] as string[],
  options: [
    { key: 'A', content: '' },
    { key: 'B', content: '' }
  ] as OptionForm[],
  correctKeys: ['A'] as string[],
  trueFalseValue: true,
  blanks: [
    { id: 'blank-1', answers: '', caseSensitive: false, trimWhitespace: true }
  ] as BlankForm[],
  version: 1
})

const singleCorrect = computed({
  get: () => form.correctKeys[0] ?? '',
  set: (value: string) => {
    form.correctKeys = value ? [value] : []
  }
})

const typeHint = computed(
  () => typeOptions.find((item) => item.value === form.type)?.hint ?? ''
)

onMounted(async () => {
  try {
    const bank = await getBank(bankId)
    bankName.value = bank.name
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载题库失败')
    router.push('/banks')
    return
  }
  if (questionId) {
    await loadQuestion(questionId)
  }
})

async function loadQuestion(id: string) {
  loading.value = true
  try {
    const question = await getQuestion(id)
    form.type = question.type
    form.status = question.status
    form.stem = question.stem.content
    form.explanation = question.explanation?.content ?? ''
    form.difficulty = question.difficulty
    form.source = question.source ?? ''
    form.mediaIds = [...question.mediaIds]
    form.version = question.version

    if (question.type === 'SINGLE_CHOICE' || question.type === 'MULTIPLE_CHOICE') {
      const payload = question.payload as ChoicePayload
      form.options = payload.options.map((option) => ({ key: option.key, content: option.content }))
      form.correctKeys = [...payload.answer.correctOptionKeys]
    } else if (question.type === 'TRUE_FALSE') {
      form.trueFalseValue = (question.payload as TrueFalsePayload).answer.value
    } else {
      form.blanks = (question.payload as FillBlankPayload).blanks.map((blank) => ({
        id: blank.id,
        answers: blank.acceptedAnswers.join('\n'),
        caseSensitive: blank.caseSensitive ?? false,
        trimWhitespace: blank.trimWhitespace ?? true
      }))
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载题目失败')
    router.push(`/banks/${bankId}/questions`)
  } finally {
    loading.value = false
  }
}

function onTypeChange() {
  if (form.type === 'SINGLE_CHOICE' || form.type === 'MULTIPLE_CHOICE') {
    if (form.options.length < 2) {
      form.options = [
        { key: 'A', content: '' },
        { key: 'B', content: '' }
      ]
    }
    if (form.type === 'SINGLE_CHOICE') {
      form.correctKeys = form.options.some((option) => option.key === form.correctKeys[0])
        ? [form.correctKeys[0]]
        : [form.options[0].key]
    } else if (form.correctKeys.length === 0) {
      form.correctKeys = [form.options[0].key]
    }
  }
}

function letter(index: number) {
  return String.fromCharCode(65 + index)
}

function addOption() {
  const used = new Set(form.options.map((option) => option.key))
  let index = 0
  while (used.has(letter(index))) {
    index += 1
  }
  form.options.push({ key: letter(index), content: '' })
}

function removeOption(index: number) {
  if (form.options.length <= 2) {
    ElMessage.warning('选择题至少需要两个选项')
    return
  }
  const [removed] = form.options.splice(index, 1)
  form.correctKeys = form.correctKeys.filter((key) => key !== removed.key)
  if (form.type === 'SINGLE_CHOICE' && form.correctKeys.length === 0) {
    form.correctKeys = [form.options[0].key]
  }
}

function addBlank() {
  form.blanks.push({
    id: `blank-${form.blanks.length + 1}`,
    answers: '',
    caseSensitive: false,
    trimWhitespace: true
  })
}

function removeBlank(index: number) {
  if (form.blanks.length <= 1) {
    ElMessage.warning('填空题至少需要一个空')
    return
  }
  form.blanks.splice(index, 1)
}

function buildPayload() {
  if (form.type === 'SINGLE_CHOICE' || form.type === 'MULTIPLE_CHOICE') {
    if (form.options.some((option) => !option.content.trim())) {
      throw new Error('请填写所有选项内容')
    }
    if (form.correctKeys.length === 0) {
      throw new Error('请选择正确答案')
    }
    if (form.type === 'SINGLE_CHOICE' && form.correctKeys.length !== 1) {
      throw new Error('单选题只能有一个正确答案')
    }
    const payload: ChoicePayload = {
      options: form.options.map((option) => ({
        key: option.key,
        content: option.content.trim(),
        mediaIds: []
      })),
      answer: { correctOptionKeys: [...form.correctKeys] },
      scoring: 'ALL_OR_NOTHING'
    }
    return payload
  }
  if (form.type === 'TRUE_FALSE') {
    const payload: TrueFalsePayload = { answer: { value: form.trueFalseValue } }
    return payload
  }
  const blanks = form.blanks.map((blank) => {
    const acceptedAnswers = blank.answers
      .split(/\r?\n|,/)
      .map((item) => item.trim())
      .filter(Boolean)
    if (acceptedAnswers.length === 0) {
      throw new Error(`填空 ${blank.id} 至少需要一个可接受答案`)
    }
    return {
      id: blank.id,
      acceptedAnswers,
      caseSensitive: blank.caseSensitive,
      trimWhitespace: blank.trimWhitespace
    }
  })
  const payload: FillBlankPayload = { blanks }
  return payload
}

async function handleUpload(options: UploadRequestOptions) {
  uploading.value = true
  try {
    const media = await uploadMedia(options.file as File)
    if (!form.mediaIds.includes(media.id)) {
      form.mediaIds.push(media.id)
    }
    appendMediaMarkdown(media.id)
    ElMessage.success(media.deduped ? '图片已存在，已复用' : '图片已上传')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '图片上传失败')
  } finally {
    uploading.value = false
  }
}

function appendMediaMarkdown(mediaId: string) {
  const marker = `media://${mediaId}`
  if (form.stem.includes(marker)) return
  const prefix = form.stem.trim() ? `${form.stem.trimEnd()}\n\n` : ''
  form.stem = `${prefix}![图片](${marker})\n`
}

function removeMedia(mediaId: string) {
  form.mediaIds = form.mediaIds.filter((id) => id !== mediaId)
  form.stem = form.stem
    .split(`![图片](media://${mediaId})`).join('')
    .split(`media://${mediaId}`).join('')
    .trimEnd()
}

async function submit() {
  if (!form.stem.trim()) {
    ElMessage.warning('请输入题干')
    return
  }
  let payload
  try {
    payload = buildPayload()
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '题目数据不完整')
    return
  }
  saving.value = true
  try {
    if (questionId) {
      await updateQuestion(questionId, {
        stem: { format: 'MARKDOWN_LATEX', content: form.stem.trim() },
        explanation: { format: 'MARKDOWN_LATEX', content: form.explanation.trim() },
        difficulty: form.difficulty,
        mediaIds: form.mediaIds,
        source: form.source.trim(),
        payload,
        status: form.status,
        version: form.version
      })
      ElMessage.success('题目已更新')
    } else {
      const input: CreateQuestionInput = {
        type: form.type,
        status: form.status,
        stem: { format: 'MARKDOWN_LATEX', content: form.stem.trim() },
        explanation: form.explanation.trim()
          ? { format: 'MARKDOWN_LATEX', content: form.explanation.trim() }
          : null,
        difficulty: form.difficulty,
        mediaIds: form.mediaIds,
        source: form.source.trim() || null,
        payload
      }
      await createQuestion(bankId, input)
      ElMessage.success('题目已创建')
    }
    router.push(`/banks/${bankId}/questions`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div v-loading="loading">
    <div class="page-head">
      <div>
        <el-button text @click="router.push(`/banks/${bankId}/questions`)">← 返回题目列表</el-button>
        <h2>{{ isEdit ? '编辑题目' : '新建题目' }}</h2>
        <p class="muted">{{ bankName }} · 内容格式为 Markdown + LaTeX，图片使用 media:// 引用。</p>
      </div>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </div>

    <el-card shadow="never" class="section">
      <el-form label-width="90px">
        <el-form-item label="题型" required>
          <el-radio-group v-model="form.type" :disabled="isEdit" @change="onTypeChange">
            <el-radio-button v-for="item in typeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
          <div class="hint">{{ typeHint }}</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 160px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已归档" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-rate v-model="form.difficulty" :max="5" show-score />
        </el-form-item>
        <el-form-item label="题干" required>
          <el-input
            v-model="form.stem"
            type="textarea"
            :rows="6"
            maxlength="20000"
            show-word-limit
            placeholder="支持 Markdown 与 $...$ / $$...$$ LaTeX 公式"
          />
        </el-form-item>
        <el-form-item label="解析">
          <el-input
            v-model="form.explanation"
            type="textarea"
            :rows="3"
            maxlength="20000"
            placeholder="可选，作答后展示"
          />
        </el-form-item>
        <el-form-item label="来源">
          <el-input v-model="form.source" maxlength="200" placeholder="例如：自编、教材、真题" />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-head">
          <span>题目图片</span>
          <el-upload
            :show-file-list="false"
            :http-request="handleUpload"
            accept="image/png,image/jpeg,image/webp,image/gif"
          >
            <el-button :loading="uploading">上传图片</el-button>
          </el-upload>
        </div>
      </template>
      <div v-if="form.mediaIds.length" class="media-gallery">
        <div v-for="mediaId in form.mediaIds" :key="mediaId" class="media-thumb">
          <MediaImage :media-id="mediaId" alt="题目图片" />
          <el-button link type="danger" @click="removeMedia(mediaId)">移除</el-button>
        </div>
      </div>
      <p v-else class="hint">支持 PNG/JPEG/WebP/GIF，单张不超过 10 MB。上传后会自动插入 media:// 引用。</p>
    </el-card>

    <el-card v-if="form.type === 'SINGLE_CHOICE' || form.type === 'MULTIPLE_CHOICE'" shadow="never" class="section">
      <template #header>
        <div class="card-head">
          <span>选项与答案</span>
          <el-button size="small" @click="addOption">添加选项</el-button>
        </div>
      </template>
      <div v-for="(option, index) in form.options" :key="option.key" class="option-row">
        <span class="option-key">{{ option.key }}</span>
        <el-input v-model="option.content" placeholder="选项内容" />
        <el-radio
          v-if="form.type === 'SINGLE_CHOICE'"
          v-model="singleCorrect"
          :value="option.key"
          class="correct"
        >
          正确
        </el-radio>
        <el-checkbox
          v-else
          v-model="form.correctKeys"
          :value="option.key"
          class="correct"
        >
          正确
        </el-checkbox>
        <el-button link type="danger" @click="removeOption(index)">删除</el-button>
      </div>
    </el-card>

    <el-card v-if="form.type === 'TRUE_FALSE'" shadow="never" class="section">
      <el-form label-width="90px">
        <el-form-item label="正确答案">
          <el-radio-group v-model="form.trueFalseValue">
            <el-radio :value="true">正确</el-radio>
            <el-radio :value="false">错误</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="form.type === 'FILL_BLANK'" shadow="never" class="section">
      <template #header>
        <div class="card-head">
          <span>填空答案</span>
          <el-button size="small" @click="addBlank">添加填空</el-button>
        </div>
      </template>
      <div v-for="(blank, index) in form.blanks" :key="blank.id" class="blank-card">
        <div class="card-head">
          <strong>空 {{ blank.id }}</strong>
          <el-button link type="danger" @click="removeBlank(index)">删除</el-button>
        </div>
        <el-input
          v-model="blank.answers"
          type="textarea"
          :rows="3"
          placeholder="每行一个可接受答案，也可用英文逗号分隔"
        />
        <div class="switch-row">
          <el-checkbox v-model="blank.caseSensitive">区分大小写</el-checkbox>
          <el-checkbox v-model="blank.trimWhitespace">忽略首尾空白</el-checkbox>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="section">
      <template #header><span>题目预览</span></template>
      <MarkdownContent v-if="form.stem.trim()" :content="form.stem" />
      <p v-else class="hint">输入题干后显示 Markdown/LaTeX 预览。</p>
      <div v-if="form.explanation.trim()" class="explanation-preview">
        <h4>解析</h4>
        <MarkdownContent :content="form.explanation" />
      </div>
    </el-card>

    <div class="actions">
      <el-button @click="router.push(`/banks/${bankId}/questions`)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存题目</el-button>
    </div>
  </div>
</template>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: flex-start; }
h2 { margin: 4px 0; }
.muted { margin: 0 0 16px; color: #64748b; }
.section { margin-bottom: 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.hint { margin-top: 6px; color: #94a3b8; font-size: 12px; }
.option-row { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
.option-key { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 8px; background: #f1f5f9; font-weight: 600; }
.correct { flex: none; }
.blank-card { padding: 12px; margin-bottom: 12px; border: 1px solid #e5e7eb; border-radius: 10px; }
.switch-row { display: flex; gap: 16px; margin-top: 8px; }
.media-gallery { display: flex; flex-wrap: wrap; gap: 16px; }
.media-thumb { width: 180px; padding: 8px; border: 1px solid #e5e7eb; border-radius: 10px; text-align: center; }
.media-thumb :deep(.media-image) { max-height: 140px; margin: 0 auto 8px; }
.explanation-preview { margin-top: 16px; padding-top: 12px; border-top: 1px dashed #e5e7eb; }
.actions { display: flex; justify-content: flex-end; gap: 12px; }
</style>
