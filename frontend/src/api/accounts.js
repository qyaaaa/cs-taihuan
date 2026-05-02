import { postJson, request } from './http'

export const listAccounts = () => request('/api/accounts')

export const createAccount = (payload = {}) => postJson('/api/accounts', payload)

export const updateAccount = (accountId, payload) => request(`/api/accounts/${accountId}`, {
  method: 'PUT',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(payload),
})

export const deleteAccount = (accountId) => request(`/api/accounts/${accountId}`, { method: 'DELETE' })
