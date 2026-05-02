import { postJson, request } from './http'

const accountPath = (accountId, path) => accountId
  ? `/api/accounts/${accountId}/session${path}`
  : `/api/buff/session${path}`

export const getSessionStatus = (accountId = null) => request(accountPath(accountId, '/status'))

export const importSession = (cookie, accountId = null) => postJson(accountPath(accountId, '/import'), {
  cookie,
  source: 'frontend-manual',
})

export const validateSessionApi = (accountId = null) => postJson(accountPath(accountId, '/validate'), {})

export const deleteSession = (accountId = null) => request(accountPath(accountId, ''), { method: 'DELETE' })
