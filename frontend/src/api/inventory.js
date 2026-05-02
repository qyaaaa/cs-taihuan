import { postJson } from './http'

const accountPath = (accountId, path) => accountId
  ? `/api/accounts/${accountId}/inventory${path}`
  : `/api/buff/inventory${path}`

export const createInventoryFetchTask = (payload, accountId = null) => postJson(accountPath(accountId, '/fetch/task'), payload)

export const createInventoryForceFetchTask = (payload, accountId = null) => postJson(accountPath(accountId, '/fetch/force/task'), payload)

export const loadInventoryPage = (payload, accountId = null) => postJson(accountPath(accountId, '/page'), payload)
