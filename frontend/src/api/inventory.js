import { postJson } from './http'

const accountPath = (accountId, path) => accountId
  ? `/api/accounts/${accountId}/inventory${path}`
  : `/api/buff/inventory${path}`

export const createInventoryFetchTask = (payload, accountId = null) => postJson(accountPath(accountId, '/fetch/task'), payload)

export const createInventoryForceFetchTask = (payload, accountId = null) => postJson(accountPath(accountId, '/fetch/force/task'), payload)

export const loadInventoryPage = (payload, accountId = null) => postJson(accountPath(accountId, '/page'), payload)

// 按磨损精估指定库存件的市值（每件查一次 BUFF 挂单最低价；档内低磨损有溢价，档地板价会低估）。
export const refineFloatPricesApi = (assetIds, accountId = null) => postJson(accountPath(accountId, '/refine-float-prices'), { assetIds })
