import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createCatalogSyncTask } from '../api/catalog'
import { optimizeTradeUp, persistNextTierCatalogApi } from '../api/tradeUp'

const PLAN_TOP_K = 10
const RARITY_FILTER_OPTIONS = ['consumer', 'industrial', 'mil-spec', 'restricted', 'classified', 'covert']

export const usePlans = ({ inventoryState, pollTask, updateCatalogTask, accountId }) => {
  const loadingPlans = ref(false)
  const loadingNextTier = ref(false)
  const loadingCatalog = ref(false)
  const selectedPlanIndex = ref(0)
  const catalogMissing = ref(false)

  const planForm = reactive({
    saleFeeRate: null,
    maxItemsPerRarity: null,
    maxCombinations: null,
  })

  const planFilters = reactive({
    sortBy: 'expectedOutputValue',
    rarity: 'all',
    trackType: 'all',
    contractType: 'all',
  })

  const planState = reactive({
    plans: [],
    lastAction: '尚未生成方案',
    nextTierAction: '尚未保存关联档位冗余数据',
    catalogAction: '尚未同步目录数据',
    catalogIncomplete: false,
  })

  const rarityOptions = computed(() => RARITY_FILTER_OPTIONS)

  const sortedPlans = computed(() => {
    return [...(planState.plans || [])]
      .filter((plan) => {
        if (planFilters.rarity !== 'all' && plan.rarity !== planFilters.rarity) {
          return false
        }
        if (planFilters.trackType === 'stattrak' && !isStatTrakPlan(plan)) {
          return false
        }
        if (planFilters.trackType === 'normal' && isStatTrakPlan(plan)) {
          return false
        }
        if (planFilters.contractType === 'gold' && !isGoldContract(plan)) {
          return false
        }
        if (planFilters.contractType === 'regular' && isGoldContract(plan)) {
          return false
        }
        return true
      })
      .sort((left, right) => comparePlans(left, right, planFilters.sortBy))
  })

  const selectedPlan = computed(() => sortedPlans.value[selectedPlanIndex.value] || null)

  const resolveAccountId = () => accountId?.value || null

  const resetPlans = () => {
    selectedPlanIndex.value = 0
    catalogMissing.value = false
    planState.plans = []
    planState.lastAction = '尚未生成方案'
    planState.nextTierAction = '尚未保存关联档位冗余数据'
    planState.catalogAction = '尚未同步目录数据'
    planState.catalogIncomplete = false
  }

  watch(sortedPlans, (plans) => {
    if (selectedPlanIndex.value >= plans.length) {
      selectedPlanIndex.value = 0
    }
  })

  const updatePlanFilter = (key, value) => {
    if (Object.prototype.hasOwnProperty.call(planFilters, key)) {
      planFilters[key] = value
      selectedPlanIndex.value = 0
    }
  }

  const optimizePlans = async () => {
    loadingPlans.value = true
    try {
      const payload = await optimizeTradeUp({
        snapshotId: inventoryState.snapshotId,
        topK: PLAN_TOP_K,
        saleFeeRate: planForm.saleFeeRate,
        maxItemsPerRarity: planForm.maxItemsPerRarity,
        maxCombinations: planForm.maxCombinations,
        sortBy: planFilters.sortBy,
        rarity: planFilters.rarity,
        trackType: planFilters.trackType,
        contractType: planFilters.contractType,
      }, resolveAccountId())
      planState.plans = dedupePlans((payload.plans || []).map(normalizePlan))
      planState.lastAction = `已生成 ${planState.plans.length} 条推荐方案`
      selectedPlanIndex.value = 0
      catalogMissing.value = false
      planState.catalogIncomplete = false
      ElMessage.success(`完成方案计算，共 ${planState.plans.length} 条`)
    } catch (error) {
      const message = String(error.message || '生成方案失败')
      if (message.includes('Catalog 数据库为空')) {
        catalogMissing.value = true
        ElMessage.error('目录数据库为空，请先点击“从 BUFF 同步目录数据”')
      } else if (message.includes('目录同步尚未完成')) {
        planState.catalogIncomplete = true
        planState.lastAction = message
        ElMessage.error(message)
      } else {
        ElMessage.error(message || '生成方案失败')
      }
    } finally {
      loadingPlans.value = false
    }
  }

  const syncCatalog = async () => {
    loadingCatalog.value = true
    try {
      const task = await createCatalogSyncTask({
        snapshotId: inventoryState.snapshotId,
      }, resolveAccountId())
      updateCatalogTask(task)
      const finalTask = await pollTask(task.taskId, updateCatalogTask)
      const payload = finalTask.result || {}
      planState.catalogAction = payload.message || `已同步 ${payload.itemCount} 条目录数据`
      planState.catalogIncomplete = Boolean(payload.partial || Number(payload.remainingGoodsCount || 0) > 0)
      catalogMissing.value = false
      if (planState.catalogIncomplete) {
        ElMessage.warning(payload.message || '目录数据仍未完整，请继续同步。')
      } else {
        ElMessage.success(payload.message || `已同步 ${payload.itemCount} 条目录数据`)
      }
    } catch (error) {
      const message = String(error.message || '同步目录数据失败')
      ElMessage.error(message || '同步目录数据失败')
    } finally {
      loadingCatalog.value = false
    }
  }

  const persistNextTierCatalog = async () => {
    loadingNextTier.value = true
    try {
      const payload = await persistNextTierCatalogApi({
        snapshotId: inventoryState.snapshotId,
      }, resolveAccountId())
      planState.nextTierAction = payload.message || `已保存 ${payload.itemCount} 条关联档位冗余数据`
      planState.catalogIncomplete = false
      ElMessage.success(payload.message || `已保存 ${payload.itemCount} 条关联档位冗余数据`)
    } catch (error) {
      const message = String(error.message || '保存关联档位冗余数据失败')
      if (message.includes('Catalog 数据库为空')) {
        catalogMissing.value = true
        ElMessage.error('目录数据库为空，请先点击“从 BUFF 同步目录数据”')
      } else if (message.includes('目录同步尚未完成')) {
        planState.catalogIncomplete = true
        planState.nextTierAction = message
        ElMessage.error(message)
      } else {
        ElMessage.error(message || '保存关联档位冗余数据失败')
      }
    } finally {
      loadingNextTier.value = false
    }
  }

  return {
    loadingPlans,
    loadingNextTier,
    loadingCatalog,
    catalogMissing,
    selectedPlanIndex,
    planForm,
    planFilters,
    rarityOptions,
    planState,
    sortedPlans,
    selectedPlan,
    resetPlans,
    updatePlanFilter,
    optimizePlans,
    syncCatalog,
    persistNextTierCatalog,
  }
}

const comparePlans = (left, right, sortBy) => {
  const leftValue = sortValue(left, sortBy)
  const rightValue = sortValue(right, sortBy)
  const direction = sortBy === 'inputCost' ? 1 : -1
  const primary = (leftValue - rightValue) * direction
  if (primary !== 0) {
    return primary
  }
  const byEv = Number(right.expectedOutputValue || 0) - Number(left.expectedOutputValue || 0)
  if (byEv !== 0) {
    return byEv
  }
  return Number(right.expectedProfit || 0) - Number(left.expectedProfit || 0)
}

const normalizePlan = (plan) => {
  if (!plan) {
    return plan
  }
  return {
    ...plan,
    inputCost: numberValue(plan.inputCost, plan.input_cost),
    expectedOutputValue: numberValue(plan.expectedOutputValue, plan.expected_output_value),
    expectedProfit: numberValue(plan.expectedProfit, plan.expected_profit),
    averageInputFloat: numberValue(plan.averageInputFloat, plan.average_input_float),
    roi: numberValue(plan.roi),
    inputs: (plan.inputs || []).map(normalizeInventoryItem),
    outcomes: (plan.outcomes || []).map(normalizeOutcome),
  }
}

const dedupePlans = (plans) => {
  const seen = new Set()
  return plans.filter((plan) => {
    const signature = planSignature(plan)
    if (seen.has(signature)) {
      return false
    }
    seen.add(signature)
    return true
  })
}

const planSignature = (plan) => {
  const inputs = (plan?.inputs || [])
    .map((item) => `${String(item?.goodsId || item?.goods_id || item?.name || '').trim()}@${String(item?.collection || '').trim()}`)
    .sort()
    .join('|')
  return `${String(plan?.rarity || '').trim()}|${inputs}`
}

const normalizeInventoryItem = (item) => {
  if (!item) {
    return item
  }
  return {
    ...item,
    assetId: item.assetId || item.asset_id,
    floatValue: item.floatValue ?? item.float_value,
    floatValueRaw: item.floatValueRaw || item.float_value_raw,
    imageUrl: item.imageUrl || item.image_url,
    wearName: item.wearName || item.wear_name,
    categoryKey: item.categoryKey || item.category_key,
    filterRarity: item.filterRarity || item.filter_rarity,
    qualityLabel: item.qualityLabel || item.quality_label,
    goodsId: item.goodsId || item.goods_id,
  }
}

const normalizeOutcome = (outcome) => {
  if (!outcome) {
    return outcome
  }
  return {
    ...outcome,
    estimatedFloat: numberValue(outcome.estimatedFloat, outcome.estimated_float),
    estimatedSalePrice: numberValue(outcome.estimatedSalePrice, outcome.estimated_sale_price),
    skin: normalizeCatalogSkin(outcome.skin),
  }
}

const normalizeCatalogSkin = (skin) => {
  if (!skin) {
    return skin
  }
  return {
    ...skin,
    goodsId: skin.goodsId || skin.goods_id,
    categoryKey: skin.categoryKey || skin.category_key,
    qualityLabel: skin.qualityLabel || skin.quality_label,
    minFloat: numberValue(skin.minFloat, skin.min_float),
    maxFloat: numberValue(skin.maxFloat, skin.max_float),
  }
}

const numberValue = (...values) => {
  for (const value of values) {
    if (value !== undefined && value !== null && value !== '') {
      return Number(value)
    }
  }
  return 0
}

const isStatTrakPlan = (plan) => {
  return (plan?.inputs || []).some((item) => /stattrak/i.test(item?.name || ''))
}

const isGoldContract = (plan) => plan?.rarity === 'covert'

const rarityRanks = {
  consumer: 1,
  industrial: 2,
  'mil-spec': 3,
  restricted: 4,
  classified: 5,
  covert: 6,
  gold: 7,
}

const sortValue = (plan, sortBy) => {
  if (sortBy === 'rarityRank') {
    const rarity = typeof plan === 'string' ? plan : plan?.rarity
    return rarityRanks[rarity] || 0
  }
  return Number(plan?.[sortBy] || 0)
}
