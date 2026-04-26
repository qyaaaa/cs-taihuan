import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createCatalogSyncTask } from '../api/catalog'
import { optimizeTradeUp, persistNextTierCatalogApi } from '../api/tradeUp'

const PLAN_TOP_K = 10

export const usePlans = ({ inventoryState, pollTask, updateCatalogTask }) => {
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
  })

  const rarityOptions = computed(() => {
    const rarities = new Set()
    ;(planState.plans || []).forEach((plan) => {
      if (plan?.rarity) {
        rarities.add(plan.rarity)
      }
    })
    return Array.from(rarities).sort((left, right) => sortValue(left, 'rarityRank') - sortValue(right, 'rarityRank'))
  })

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
      })
      planState.plans = payload.plans || []
      planState.lastAction = `已生成 ${planState.plans.length} 条推荐方案`
      selectedPlanIndex.value = 0
      catalogMissing.value = false
      ElMessage.success(`完成方案计算，共 ${planState.plans.length} 条`)
    } catch (error) {
      const message = String(error.message || '生成方案失败')
      if (message.includes('Catalog 数据库为空')) {
        catalogMissing.value = true
        ElMessage.error('目录数据库为空，请先点击“从 BUFF 同步目录数据”')
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
      })
      updateCatalogTask(task)
      const finalTask = await pollTask(task.taskId, updateCatalogTask)
      const payload = finalTask.result || {}
      planState.catalogAction = payload.message || `已同步 ${payload.itemCount} 条目录数据`
      catalogMissing.value = false
      ElMessage.success(payload.message || `已同步 ${payload.itemCount} 条目录数据`)
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
      })
      planState.nextTierAction = payload.message || `已保存 ${payload.itemCount} 条关联档位冗余数据`
      ElMessage.success(payload.message || `已保存 ${payload.itemCount} 条关联档位冗余数据`)
    } catch (error) {
      const message = String(error.message || '保存关联档位冗余数据失败')
      if (message.includes('Catalog 数据库为空')) {
        catalogMissing.value = true
        ElMessage.error('目录数据库为空，请先点击“从 BUFF 同步目录数据”')
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
