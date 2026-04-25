import { postJson } from './http'

export const optimizeTradeUp = (payload) => postJson('/api/trade-up/optimize', payload)

export const persistNextTierCatalogApi = (payload) => postJson('/api/trade-up/next-tier/persist', payload)
