import { apiFetch } from './http'

export type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'FILL_BLANK'
export type QuestionStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export interface ContentDoc {
  format: 'MARKDOWN_LATEX'
  content: string
}

export interface QuestionOption {
  key: string
  content: string
  mediaIds: string[]
}

export interface ChoicePayload {
  options: QuestionOption[]
  answer: { correctOptionKeys: string[] }
  scoring?: 'ALL_OR_NOTHING'
}

export interface TrueFalsePayload {
  answer: { value: boolean }
}

export interface FillBlankPayload {
  blanks: {
    id: string
    acceptedAnswers: string[]
    caseSensitive?: boolean
    trimWhitespace?: boolean
  }[]
}

export type QuestionPayload = ChoicePayload | TrueFalsePayload | FillBlankPayload

export interface QuestionView {
  id: string
  bankId: string
  type: QuestionType
  status: QuestionStatus
  stem: ContentDoc
  explanation: ContentDoc | null
  difficulty: number
  mediaIds: string[]
  tagIds: string[]
  knowledgePointIds: string[]
  source: string | null
  payload: QuestionPayload
  version: number
  createdAt: string
  updatedAt: string
}

export interface QuestionPage {
  items: QuestionView[]
  nextCursor: string | null
  hasMore: boolean
}

export interface CreateQuestionInput {
  type: QuestionType
  status?: QuestionStatus
  stem: ContentDoc
  explanation?: ContentDoc | null
  difficulty?: number
  mediaIds?: string[]
  source?: string | null
  payload: QuestionPayload
}

export interface UpdateQuestionInput {
  stem?: ContentDoc
  explanation?: ContentDoc | null
  difficulty?: number
  mediaIds?: string[]
  source?: string | null
  payload?: QuestionPayload
  status?: QuestionStatus
  version: number
}

export interface QuestionVersionItem {
  versionNo: number
  createdBy: string
  createdAt: string
}

function queryString(params: Record<string, string | number | undefined | null>) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  })
  const text = search.toString()
  return text ? `?${text}` : ''
}

export function listQuestions(bankId: string, params: {
  type?: string
  status?: string
  q?: string
  tagId?: string
  knowledgePointId?: string
  cursor?: string | null
  limit?: number
}) {
  return apiFetch<QuestionPage>(`/banks/${bankId}/questions${queryString(params)}`)
}

export function searchQuestions(params: {
  q?: string
  bankId?: string
  type?: string
  status?: string
  cursor?: string | null
  limit?: number
}) {
  return apiFetch<QuestionPage>(`/questions/search${queryString(params)}`)
}

export function getQuestion(questionId: string) {
  return apiFetch<QuestionView>(`/questions/${questionId}`)
}

export function createQuestion(bankId: string, input: CreateQuestionInput) {
  return apiFetch<QuestionView>(`/banks/${bankId}/questions`, {
    method: 'POST',
    body: JSON.stringify(input)
  })
}

export function updateQuestion(questionId: string, input: UpdateQuestionInput) {
  return apiFetch<QuestionView>(`/questions/${questionId}`, {
    method: 'PATCH',
    body: JSON.stringify(input)
  })
}

export function deleteQuestion(questionId: string) {
  return apiFetch<void>(`/questions/${questionId}`, { method: 'DELETE' })
}

export function publishQuestion(questionId: string) {
  return apiFetch<QuestionView>(`/questions/${questionId}/publish`, {
    method: 'POST',
    body: '{}'
  })
}

export function archiveQuestion(questionId: string) {
  return apiFetch<QuestionView>(`/questions/${questionId}/archive`, {
    method: 'POST',
    body: '{}'
  })
}

export function duplicateQuestion(questionId: string, targetBankId: string) {
  return apiFetch<QuestionView>(`/questions/${questionId}/duplicate`, {
    method: 'POST',
    body: JSON.stringify({ targetBankId })
  })
}

export function listQuestionVersions(questionId: string) {
  return apiFetch<{ items: QuestionVersionItem[] }>(`/questions/${questionId}/versions`)
}
