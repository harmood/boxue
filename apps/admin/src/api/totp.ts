import { adminFetch } from './http'

export interface TotpStatus {
  enabled: boolean
  confirmedAt: string | null
}

export interface TotpSetup {
  secret: string
  otpauthUri: string
}

export function getTotpStatus() {
  return adminFetch<TotpStatus>('/admin/auth/totp/status')
}

export function setupTotp(password: string) {
  return adminFetch<TotpSetup>('/admin/auth/totp/setup', {
    method: 'POST',
    body: JSON.stringify({ password })
  })
}

export function enableTotp(code: string) {
  return adminFetch<TotpStatus>('/admin/auth/totp/enable', {
    method: 'POST',
    body: JSON.stringify({ code })
  })
}

export function disableTotp(password: string, code: string) {
  return adminFetch<void>('/admin/auth/totp/disable', {
    method: 'POST',
    body: JSON.stringify({ password, code })
  })
}
