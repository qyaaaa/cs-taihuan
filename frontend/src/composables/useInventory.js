import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createInventoryFetchTask, createInventoryForceFetchTask, loadInventoryPage } from '../api/inventory'
import { currency } from '../utils/formatters'

const normalizeInventoryItem = (item) => {
  if (!item) {
    return item
  }
  return {
    ...item,
    assetId: item.assetId ?? item.asset_id ?? null,
    floatValue: item.floatValue ?? item.float_value ?? null,
    floatValueRaw: item.floatValueRaw ?? item.float_value_raw ?? null,
    imageUrl: item.imageUrl ?? item.image_url ?? '',
    wearName: item.wearName ?? item.wear_name ?? '',
    qualityLabel: item.qualityLabel ?? item.quality_label ?? '',
    goodsId: item.goodsId ?? item.goods_id ?? '',
    categoryKey: item.categoryKey ?? item.category_key ?? '',
    filterRarity: item.filterRarity ?? item.filter_rarity ?? '',
  }
}

export const useInventory = ({ pollTask, updateInventoryTask }) => {
  const loadingInventory = ref(false)
  const inventoryForm = reactive({
    outputPath: 'data/buff_inventory.json',
    inventoryPath: 'data/buff_inventory.json',
    game: 'csgo',
    pageSize: 80,
    maxPages: null,
    forceRefresh: true,
  })

  const inventoryState = reactive({
    snapshotId: null,
    itemCount: 0,
    tradableCount: 0,
    withFloatCount: 0,
    totalCost: 0,
    totalItems: 0,
    currentPage: 1,
    pageSize: 50,
    outputPath: '',
    items: [],
    usePersistedPaging: false,
    lastAction: '尚未加载库存',
  })

  const inventoryStats = computed(() => {
    const items = inventoryState.items || []
    const totalCost = inventoryState.usePersistedPaging
      ? Number(inventoryState.totalCost || 0)
      : items.reduce((sum, item) => sum + Number(item.price || 0), 0)
    const tradableCount = inventoryState.usePersistedPaging
      ? Number(inventoryState.tradableCount || 0)
      : items.filter((item) => item.tradable !== false).length
    const withFloatCount = inventoryState.usePersistedPaging
      ? Number(inventoryState.withFloatCount || 0)
      : items.filter((item) => item.floatValue !== null && item.floatValue !== undefined).length
    const itemCount = inventoryState.usePersistedPaging ? Number(inventoryState.itemCount || 0) : items.length
    return [
      { label: '素材总数', value: String(itemCount).padStart(2, '0') },
      { label: '可交易', value: String(tradableCount).padStart(2, '0') },
      { label: '有磨损值', value: String(withFloatCount).padStart(2, '0') },
      { label: '库存总成本', value: currency(totalCost) },
    ]
  })

  const inventoryItems = computed(() => inventoryState.items || [])

  const normalizeInventory = (payload, actionLabel) => {
    inventoryState.snapshotId = payload.snapshotId || null
    inventoryState.itemCount = payload.itemCount || 0
    inventoryState.outputPath = payload.outputPath || ''
    inventoryState.items = (payload.items || []).map(normalizeInventoryItem)
    inventoryState.usePersistedPaging = false
    inventoryState.lastAction = payload.message || actionLabel
    if (inventoryState.outputPath) {
      inventoryForm.inventoryPath = inventoryState.outputPath
    }
  }

  const applyPagedInventory = (payload) => {
    inventoryState.snapshotId = payload.snapshotId || inventoryState.snapshotId
    inventoryState.itemCount = payload.itemCount || 0
    inventoryState.tradableCount = payload.tradableCount || 0
    inventoryState.withFloatCount = payload.withFloatCount || 0
    inventoryState.totalCost = payload.totalCost || 0
    inventoryState.totalItems = payload.totalItems || payload.itemCount || 0
    inventoryState.currentPage = payload.currentPage || 1
    inventoryState.pageSize = payload.pageSize || 50
    inventoryState.items = (payload.items || []).map(normalizeInventoryItem)
    inventoryState.usePersistedPaging = true
  }

  const loadPersistedInventoryPage = async (page = 1, snapshotId = inventoryState.snapshotId) => {
    const payload = await loadInventoryPage({
      snapshotId,
      game: inventoryForm.game,
      page,
      pageSize: 50,
    })
    applyPagedInventory(payload)
  }

  const restorePersistedInventory = async () => {
    loadingInventory.value = true
    try {
      await loadPersistedInventoryPage(1, null)
      inventoryState.lastAction = '已从数据库载入最近一次保存的武器库存'
    } catch (error) {
      if (String(error.message || '').includes('No persisted inventory snapshot was found')) {
        inventoryState.lastAction = '当前没有可用的数据库库存快照，请先抓取一次 BUFF 库存。'
        return
      }
      throw error
    } finally {
      loadingInventory.value = false
    }
  }

  const fetchInventory = async () => {
    loadingInventory.value = true
    try {
      const task = await createInventoryFetchTask({
        outputPath: inventoryForm.outputPath,
        game: inventoryForm.game,
        pageSize: inventoryForm.pageSize,
        maxPages: inventoryForm.maxPages || null,
        forceRefresh: inventoryForm.forceRefresh,
      })
      updateInventoryTask(task)
      const finalTask = await pollTask(task.taskId, updateInventoryTask)
      const payload = finalTask.result || {}
      normalizeInventory(payload, '已从 BUFF 抓取并保存库存')
      if (payload.snapshotId) {
        await loadPersistedInventoryPage(1, payload.snapshotId)
      }
      ElMessage.success(payload.message || `已同步 ${payload.itemCount} 件素材`)
    } catch (error) {
      ElMessage.error(error.message || '抓取库存失败')
    } finally {
      loadingInventory.value = false
    }
  }

  const forceFetchInventory = async () => {
    loadingInventory.value = true
    try {
      const task = await createInventoryForceFetchTask({
        outputPath: inventoryForm.outputPath,
        game: inventoryForm.game,
        pageSize: inventoryForm.pageSize,
        maxPages: inventoryForm.maxPages || null,
        forceRefresh: true,
      })
      updateInventoryTask(task)
      const finalTask = await pollTask(task.taskId, updateInventoryTask)
      const payload = finalTask.result || {}
      normalizeInventory(payload, '已强制从 BUFF 抓取并保存库存')
      if (payload.snapshotId) {
        await loadPersistedInventoryPage(1, payload.snapshotId)
      }
      ElMessage.success(payload.message || `已强制同步 ${payload.itemCount} 件素材`)
    } catch (error) {
      ElMessage.error(error.message || '强制抓取库存失败')
    } finally {
      loadingInventory.value = false
    }
  }

  const changeInventoryPage = async (page) => {
    loadingInventory.value = true
    try {
      await loadPersistedInventoryPage(page)
    } catch (error) {
      ElMessage.error(error.message || '翻页失败')
    } finally {
      loadingInventory.value = false
    }
  }

  return {
    loadingInventory,
    inventoryForm,
    inventoryState,
    inventoryStats,
    inventoryItems,
    restorePersistedInventory,
    fetchInventory,
    forceFetchInventory,
    changeInventoryPage,
  }
}
