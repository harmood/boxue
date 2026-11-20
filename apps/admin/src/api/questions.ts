import { adminFetch } from './http'

export interface AdminQuestionItem {
  id: string
  ownerId: string
  ownerEmail: string
  bankId: string
  bankName: string
  type: string
  status: string
  stem: { format: string; content: string }
  explanation: { format: string; content: string } | null
  difficulty: number
  mediaIds: string[]
  tagIds: string[]
  knowledgePointIds: string[]
  source: string | null
  payload: Record<string, unknown>
  version: number
  createdAt: string
  updatedAt: string
}

export interface AdminQuestionPage {
  items: AdminQuestionItem[]
  nextCursor: string | null
  hasMore: boolean
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

export function listQuestions(params: {
  q?: string
  bankId?: string
  ownerId?: string
  type?: string
  status?: string
  cursor?: string | null
  limit?: number
}) {
  return adminFetch<AdminQuestionPage>(`/admin/questions${queryString(params)}`)
}

export function getQuestion(questionId: string) {
  return adminFetch<AdminQuestionItem>(`/admin/questions/${questionId}`)
}
