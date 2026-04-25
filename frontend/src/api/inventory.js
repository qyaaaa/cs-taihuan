import { postJson } from './http'

export const createInventoryFetchTask = (payload) => postJson('/api/buff/inventory/fetch/task', payload)

export const createInventoryForceFetchTask = (payload) => postJson('/api/buff/inventory/fetch/force/task', payload)

export const loadInventoryPage = (payload) => postJson('/api/buff/inventory/page', payload)
