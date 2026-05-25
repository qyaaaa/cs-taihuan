import { getJson, postJson } from './http'

const accountPath = (accountId, path) => accountId
  ? `/api/accounts/${accountId}/trade-up${path}`
  : `/api/trade-up${path}`

export const optimizeTradeUp = (payload, accountId = null) => postJson(accountPath(accountId, '/optimize'), payload)

export const persistNextTierCatalogApi = (payload, accountId = null) => postJson(accountPath(accountId, '/next-tier/persist'), payload)

export const searchFloatTargetsApi = (keyword = '', accountId = null) => {
  const params = new URLSearchParams()
  if (keyword) {
    params.set('keyword', keyword)
  }
  params.set('limit', '50')
  return getJson(`${accountPath(accountId, '/float/targets')}?${params.toString()}`)
}

export const calculateFloatApi = (payload, accountId = null) => postJson(accountPath(accountId, '/float/calculate'), payload)
