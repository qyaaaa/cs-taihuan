import { postJson } from './http'

const accountPath = (accountId, path) => accountId
  ? `/api/accounts/${accountId}/trade-up${path}`
  : `/api/trade-up${path}`

export const optimizeTradeUp = (payload, accountId = null) => postJson(accountPath(accountId, '/optimize'), payload)

export const persistNextTierCatalogApi = (payload, accountId = null) => postJson(accountPath(accountId, '/next-tier/persist'), payload)
