import { postJson } from './http'

const accountPath = (accountId) => accountId
  ? `/api/accounts/${accountId}/catalog/sync/task`
  : '/api/catalog/sync/task'

export const createCatalogSyncTask = (payload, accountId = null) => postJson(accountPath(accountId), payload)

// 定向补全某收藏品的产物档皮肤（按名搜 BUFF 补抓缺价皮肤），让方案产物池完整、期望不虚高。
export const backfillOutcomesApi = (collection, accountId = null, maxSkinSearches = 40) => {
  const base = accountId ? `/api/accounts/${accountId}/catalog` : '/api/catalog'
  const params = new URLSearchParams()
  if (collection) {
    params.set('collection', collection)
  }
  if (maxSkinSearches) {
    params.set('maxSkinSearches', String(maxSkinSearches))
  }
  return postJson(`${base}/backfill-outcomes?${params.toString()}`, {})
}
