import { postJson, request } from './http'

const accountPath = (accountId, path) => accountId
  ? `/api/accounts/${accountId}/session${path}`
  : `/api/buff/session${path}`

const qrLoginPath = (accountId, path) => accountId
  ? `/api/accounts/${accountId}/login/qrcode${path}`
  : `/api/buff/login/qrcode${path}`

export const getSessionStatus = (accountId = null) => request(accountPath(accountId, '/status'))

export const importSession = (cookie, accountId = null) => postJson(accountPath(accountId, '/import'), {
  cookie,
  source: 'frontend-manual',
})

export const validateSessionApi = (accountId = null) => postJson(accountPath(accountId, '/validate'), {})

export const deleteSession = (accountId = null) => request(accountPath(accountId, ''), { method: 'DELETE' })

// 二维码登录接口。
export const startQrLogin = (accountId = null) => postJson(qrLoginPath(accountId, '/start'), {})

export const getQrLoginStatus = (sessionId, accountId = null) =>
  request(`${qrLoginPath(accountId, '/status')}?sessionId=${encodeURIComponent(sessionId)}`)

export const cancelQrLogin = (sessionId, accountId = null) =>
  postJson(qrLoginPath(accountId, '/cancel'), { sessionId })
