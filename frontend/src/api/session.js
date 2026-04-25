import { postJson, request } from './http'

export const getSessionStatus = () => request('/api/buff/session/status')

export const importSession = (cookie) => postJson('/api/buff/session/import', {
  cookie,
  source: 'frontend-manual',
})

export const validateSessionApi = () => postJson('/api/buff/session/validate', {})

export const deleteSession = () => request('/api/buff/session', { method: 'DELETE' })
