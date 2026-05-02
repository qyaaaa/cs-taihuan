import { postJson } from './http'

const accountPath = (accountId) => accountId
  ? `/api/accounts/${accountId}/catalog/sync/task`
  : '/api/catalog/sync/task'

export const createCatalogSyncTask = (payload, accountId = null) => postJson(accountPath(accountId), payload)
