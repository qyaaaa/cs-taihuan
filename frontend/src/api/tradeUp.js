import { getJson, postJson } from './http'

const accountPath = (accountId, path) => accountId
  ? `/api/accounts/${accountId}/trade-up${path}`
  : `/api/trade-up${path}`

export const optimizeTradeUp = (payload, accountId = null) => postJson(accountPath(accountId, '/optimize'), payload)

export const persistNextTierCatalogApi = (payload, accountId = null) => postJson(accountPath(accountId, '/next-tier/persist'), payload)

export const searchFloatTargetsApi = ({ collection = '', name = '', rarity = '' } = {}, accountId = null) => {
  const params = new URLSearchParams()
  if (collection) {
    params.set('collection', collection)
  }
  if (name) {
    params.set('name', name)
  }
  if (rarity) {
    params.set('rarity', rarity)
  }
  params.set('limit', '50')
  return getJson(`${accountPath(accountId, '/float/targets')}?${params.toString()}`)
}

export const listFloatCollectionsApi = (accountId = null) =>
  getJson(accountPath(accountId, '/float/collections'))

export const calculateFloatApi = (payload, accountId = null) => postJson(accountPath(accountId, '/float/calculate'), payload)
