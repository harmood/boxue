import { apiFetch } from './http'

export type BankVisibility = 'PRIVATE' | 'UNLISTED' | 'PUBLIC'
export type BankStatus = 'ACTIVE' | 'ARCHIVED'

export interface BankView {
  id: string
  name: string
  description: string | null
  visibility: BankVisibility
  status: BankStatus
  questionCount: number
  practicedCount: number
  accuracy: number
  version: number
  createdAt: string
  updatedAt: string
}

export interface BankPage {
  items: BankView[]
  nextCursor: string | null
  hasMore: boolean
}

export interface CreateBankInput {
  name: string
  description?: string | null
  visibility?: BankVisibility
}

export interface UpdateBankInput {
  name?: string
  description?: string | null
  status?: BankStatus
  version: number
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
  status?: string
  q?: string
  cursor?: string | null
  limit?: number
}) {
  return apiFetch<BankPage>(`/banks${queryString(params)}`)
}

export function getBank(bankId: string) {
  return apiFetch<BankView>(`/banks/${bankId}`)
}

export function createBank(input: CreateBankInput) {
  return apiFetch<BankView>('/banks', {
    method: 'POST',
    body: JSON.stringify(input)
  })
}

export function updateBank(bankId: string, input: UpdateBankInput) {
  return apiFetch<BankView>(`/banks/${bankId}`, {
    method: 'PATCH',
    body: JSON.stringify(input)
  })
}

export function deleteBank(bankId: string, cascade = false) {
  return apiFetch<void>(`/banks/${bankId}${cascade ? '?cascade=true' : ''}`, {
    method: 'DELETE'
  })
}
