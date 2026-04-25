import { postJson } from './http'

export const createCatalogSyncTask = (payload) => postJson('/api/catalog/sync/task', payload)
