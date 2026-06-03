import { getJson } from './http'

export const listSkinCollectionsApi = () => getJson('/api/skin-float-range/collections')
