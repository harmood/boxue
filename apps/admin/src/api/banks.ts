import { adminFetch } from './http'

export interface AdminBankItem {
  id: string
  ownerId: string
  ownerEmail: string
  name: string
  description: string | null
  visibility: string
  status: string
  questionCount: number
  version: number
  createdAt: string
  updatedAt: string
}

export interface AdminBankPage {
  items: AdminBankItem[]
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

export function listBanks(params: {
  q?: string
  ownerId?: string
  status?: string
  visibility?: string
  cursor?: string | null
  limit?: number
}) {
  return adminFetch<AdminBankPage>(`/admin/banks${queryString(params)}`)
}

export function getBank(bankId: string) {
  return adminFetch<AdminBankItem>(`/admin/banks/${bankId}`)
}
