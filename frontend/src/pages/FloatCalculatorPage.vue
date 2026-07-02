<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { loadInventoryPage } from '../api/inventory'
import { calculateFloatApi, listFloatCollectionsApi, searchFloatTargetsApi } from '../api/tradeUp'

const props = defineProps({
  accountId: {
    type: [Number, String],
    default: null,
  },
  snapshotId: {
    type: [Number, String],
    default: null,
  },
})

const loadingTargets = ref(false)
const loadingInventoryCandidates = ref(false)
const calculating = ref(false)
const targetOptions = ref([])
const collectionOptions = ref([])
const selectedCollection = ref('')
const selectedRarity = ref('')
const rarityOptions = [
  { value: '', label: '全部档位' },
  { value: 'mil-spec', label: '军规级（蓝）' },
  { value: 'restricted', label: '受限（紫）' },
  { value: 'classified', label: '保密（粉）' },
  { value: 'covert', label: '隐秘（红）' },
  { value: 'gold', label: '暗金（刀/手套）' },
]
const inventoryCandidates = ref([])
const result = ref(null)
const lastError = ref('')
let calculateTimer = null
let candidateRequestId = 0

const createSlot = () => ({
  mode: 'inventory',
  manualFloat: null,
  inventoryAssetId: '',
})

const form = reactive({
  // 稳定选择键：目录项用 goods_id，仅基准库项用名称。
  targetKey: '',
  targetFloat: 0.05201314,
  contractSize: 10,
  slots: Array.from({ length: 10 }, createSlot),
})

const visibleSlots = computed(() => form.slots.slice(0, form.contractSize))
const selectedTarget = computed(() => targetOptions.value.find((item) => item.key === form.targetKey) || null)
const expectedContractSize = computed(() => selectedTarget.value?.rarity === 'gold' ? 5 : 10)
const inputRarity = computed(() => previousRarity(selectedTarget.value?.rarity))
const contractSizeOptions = computed(() => [
  {
    label: expectedContractSize.value === 5 ? '5 合 1' : '10 合 1',
    value: expectedContractSize.value,
  },
])
const lockedCount = computed(() => visibleSlots.value.filter((slot) => hasSlotValue(slot)).length)
const inventoryLockedCount = computed(() => visibleSlots.value.filter((slot) => slot.mode === 'inventory' && hasSlotValue(slot)).length)
const manualLockedCount = computed(() => visibleSlots.value.filter((slot) => slot.mode === 'manual' && hasSlotValue(slot)).length)
const automaticSlotCount = computed(() => form.contractSize - lockedCount.value)
const canCalculate = computed(() => Boolean(form.targetKey && form.targetFloat !== null && form.targetFloat !== ''))
const inventoryItemsByAssetId = computed(() => {
  const map = new Map()
  inventoryCandidates.value.forEach((item) => {
    if (item.assetId) {
      map.set(String(item.assetId), item)
    }
  })
  return map
})
const selectedInventoryItems = computed(() => visibleSlots.value
  .filter((slot) => slot.mode === 'inventory' && slot.inventoryAssetId)
  .map((slot) => inventoryItemsByAssetId.value.get(String(slot.inventoryAssetId)))
  .filter(Boolean))
const selectedStatTrakType = computed(() => {
  const first = selectedInventoryItems.value[0]
  if (!first) {
    return null
  }
  return isStatTrakItem(first) ? 'stattrak' : 'normal'
})
const statusText = computed(() => {
  if (result.value?.reachable) {
    return '可达成'
  }
  if (result.value) {
    return '不可达成'
  }
  return '等待输入'
})
const hasInventorySnapshot = computed(() => Boolean(props.snapshotId))
const inventoryCandidateNote = computed(() => {
  if (hasInventorySnapshot.value) {
    return '库存候选会按目标下级档位、可交易、武器皮肤和已选 StatTrak 类型过滤。'
  }
  return '当前没有库存快照，可先手动填写磨损；导入会话并抓取库存后可选择库存饰品。'
})

const searchTargets = async (name = '', { notify = true } = {}) => {
  loadingTargets.value = true
  try {
    targetOptions.value = (await searchFloatTargetsApi(
      { collection: selectedCollection.value, name, rarity: selectedRarity.value },
      props.accountId,
    )).map(normalizeTarget)
  } catch (error) {
    targetOptions.value = []
    if (notify) {
      ElMessage.error(error.message || '加载目标饰品失败')
    }
  } finally {
    loadingTargets.value = false
  }
}

const loadCollections = async () => {
  try {
    collectionOptions.value = await listFloatCollectionsApi(props.accountId)
  } catch (error) {
    collectionOptions.value = []
  }
}

const onCollectionChange = () => {
  searchTargets('', { notify: false })
}

const onRarityChange = () => {
  // 刀/手套（gold）没有收藏品；清空并禁用收藏品筛选，避免两个条件冲突导致空结果。
  if (selectedRarity.value === 'gold') {
    selectedCollection.value = ''
  }
  searchTargets('', { notify: false })
}

const loadInventoryCandidates = async ({ notify = false } = {}) => {
  if (!hasInventorySnapshot.value) {
    candidateRequestId += 1
    inventoryCandidates.value = []
    loadingInventoryCandidates.value = false
    return
  }
  const requestId = ++candidateRequestId
  loadingInventoryCandidates.value = true
  try {
    const firstPage = await loadInventoryPage({
      snapshotId: props.snapshotId || null,
      game: 'csgo',
      page: 1,
      pageSize: 200,
      rarity: 'all',
    }, props.accountId)
    if (requestId !== candidateRequestId) {
      return
    }
    const totalItems = Number(firstPage.totalItems || firstPage.itemCount || 0)
    const pageSize = Number(firstPage.pageSize || 200)
    const totalPages = Math.max(1, Math.ceil(totalItems / pageSize))
    const items = [...(firstPage.items || [])]
    for (let page = 2; page <= totalPages; page += 1) {
      const payload = await loadInventoryPage({
        snapshotId: firstPage.snapshotId || props.snapshotId || null,
        game: 'csgo',
        page,
        pageSize,
        rarity: 'all',
      }, props.accountId)
      if (requestId !== candidateRequestId) {
        return
      }
      items.push(...(payload.items || []))
    }
    inventoryCandidates.value = dedupeInventoryItems(items.map(normalizeInventoryItem))
  } catch (error) {
    inventoryCandidates.value = []
    if (notify) {
      ElMessage.warning(error.message || '库存候选加载失败，可先手动填写磨损')
    }
  } finally {
    if (requestId === candidateRequestId) {
      loadingInventoryCandidates.value = false
    }
  }
}

const calculate = async ({ silent = false } = {}) => {
  if (!canCalculate.value) {
    result.value = null
    return
  }
  calculating.value = true
  lastError.value = ''
  try {
    result.value = normalizeResult(await calculateFloatApi({
      targetGoodsId: selectedTarget.value?.goodsId || undefined,
      targetName: selectedTarget.value?.goodsId ? undefined : selectedTarget.value?.name,
      targetFloat: Number(form.targetFloat),
      contractSize: form.contractSize,
      lockedInputFloats: visibleSlots.value.map(slotFloat),
    }, props.accountId))
    if (!silent) {
      ElMessage.success(result.value.message || '磨损计算完成')
    }
  } catch (error) {
    result.value = null
    lastError.value = error.message || '磨损计算失败'
    if (!silent) {
      ElMessage.error(lastError.value)
    }
  } finally {
    calculating.value = false
  }
}

const scheduleCalculate = () => {
  window.clearTimeout(calculateTimer)
  calculateTimer = window.setTimeout(() => calculate({ silent: true }), 260)
}

const clearSlot = (slot) => {
  slot.manualFloat = null
  slot.inventoryAssetId = ''
}

const fillSuggestedFloats = () => {
  if (!result.value?.reachable || result.value.requiredRemainingAverageFloat === null) {
    return
  }
  visibleSlots.value.forEach((slot) => {
    if (!hasSlotValue(slot)) {
      slot.mode = 'manual'
      slot.manualFloat = Number(result.value.requiredRemainingAverageFloat.toFixed(8))
    }
  })
}

const resetSlots = () => {
  form.slots.forEach((slot) => {
    slot.mode = 'inventory'
    slot.manualFloat = null
    slot.inventoryAssetId = ''
  })
}

const handleSlotModeChange = (slot, mode) => {
  slot.mode = mode
}

watch(
  () => [
    form.targetKey,
    form.targetFloat,
    form.contractSize,
    visibleSlots.value.map((slot) => `${slot.mode}:${slot.manualFloat ?? ''}:${slot.inventoryAssetId ?? ''}`).join('|'),
  ],
  scheduleCalculate
)

watch(
  expectedContractSize,
  (size) => {
    if (form.contractSize !== size) {
      form.contractSize = size
    }
  },
  { immediate: true }
)

watch(
  () => props.accountId,
  () => {
    result.value = null
    selectedCollection.value = ''
    loadCollections()
    searchTargets('', { notify: false })
    loadInventoryCandidates()
  }
)

watch(
  () => props.snapshotId,
  () => {
    loadInventoryCandidates()
  }
)

onMounted(() => {
  loadCollections()
  searchTargets('', { notify: false })
  loadInventoryCandidates()
})

const normalizeTarget = (target) => {
  const goodsId = target.goodsId || target.goods_id || ''
  return {
    ...target,
    goodsId,
    // 稳定选择键：有 goods_id 时用它，否则用唯一名称。
    key: goodsId || target.name,
    floatSource: target.floatSource || target.float_source || 'catalog',
    rarity: target.rarity,
    minFloat: numberValue(target.minFloat, target.min_float),
    maxFloat: numberValue(target.maxFloat, target.max_float),
  }
}

const normalizeResult = (payload) => ({
  ...payload,
  targetMinFloat: numberValue(payload.targetMinFloat, payload.target_min_float),
  targetMaxFloat: numberValue(payload.targetMaxFloat, payload.target_max_float),
  requiredAverageInputFloat: numberValue(payload.requiredAverageInputFloat, payload.required_average_input_float),
  requiredTotalInputFloat: numberValue(payload.requiredTotalInputFloat, payload.required_total_input_float),
  lockedFloatSum: numberValue(payload.lockedFloatSum, payload.locked_float_sum),
  lockedSlotCount: numberValue(payload.lockedSlotCount, payload.locked_slot_count),
  remainingSlotCount: numberValue(payload.remainingSlotCount, payload.remaining_slot_count),
  requiredRemainingAverageFloat: nullableNumber(payload.requiredRemainingAverageFloat, payload.required_remaining_average_float),
  allowedRemainingMinFloat: nullableNumber(payload.allowedRemainingMinFloat, payload.allowed_remaining_min_float),
  allowedRemainingMaxFloat: nullableNumber(payload.allowedRemainingMaxFloat, payload.allowed_remaining_max_float),
})

const normalizeInventoryItem = (item) => ({
  ...item,
  assetId: item.assetId ?? item.asset_id ?? '',
  goodsId: item.goodsId ?? item.goods_id ?? '',
  name: item.name ?? '',
  collection: item.collection ?? '',
  price: numberValue(item.price),
  floatValue: nullableNumber(item.floatValue, item.float_value),
  floatPrice: nullableNumber(item.floatPrice, item.float_price),
  basePrice: nullableNumber(item.basePrice, item.base_price),
  floatValueRaw: item.floatValueRaw ?? item.float_value_raw ?? '',
  imageUrl: item.imageUrl ?? item.image_url ?? '',
  filterRarity: item.filterRarity ?? item.filter_rarity ?? '',
  categoryKey: item.categoryKey ?? item.category_key ?? '',
  qualityLabel: item.qualityLabel ?? item.quality_label ?? '',
  tradable: item.tradable,
})

const dedupeInventoryItems = (items) => {
  const seen = new Set()
  return items.filter((item) => {
    const key = String(item.assetId || `${item.goodsId}:${item.floatValue}:${item.name}`)
    if (seen.has(key)) {
      return false
    }
    seen.add(key)
    return true
  })
}

const numberValue = (...values) => {
  for (const value of values) {
    if (value !== undefined && value !== null && value !== '') {
      return Number(value)
    }
  }
  return 0
}

const nullableNumber = (...values) => {
  for (const value of values) {
    if (value !== undefined && value !== null && value !== '') {
      return Number(value)
    }
  }
  return null
}

const slotInventoryItem = (slot) => inventoryItemsByAssetId.value.get(String(slot.inventoryAssetId || '')) || null

const slotFloat = (slot) => {
  if (slot.mode === 'inventory') {
    return slotInventoryItem(slot)?.floatValue ?? null
  }
  if (slot.manualFloat === null || slot.manualFloat === undefined || slot.manualFloat === '') {
    return null
  }
  return Number(slot.manualFloat)
}

const hasSlotValue = (slot) => slotFloat(slot) !== null && slotFloat(slot) !== undefined && slotFloat(slot) !== ''

const previousRarity = (rarity) => {
  const order = ['consumer', 'industrial', 'mil-spec', 'restricted', 'classified', 'covert', 'gold']
  const index = order.indexOf(rarity)
  return index > 0 ? order[index - 1] : ''
}

const selectedAssetIdsExcept = (slot) => new Set(visibleSlots.value
  .filter((item) => item !== slot && item.mode === 'inventory' && item.inventoryAssetId)
  .map((item) => String(item.inventoryAssetId)))

const candidateOptionsForSlot = (slot) => {
  const excluded = selectedAssetIdsExcept(slot)
  return inventoryCandidates.value.filter((item) => {
    if (!item.assetId || excluded.has(String(item.assetId))) {
      return false
    }
    if (inputRarity.value && item.filterRarity && item.filterRarity !== inputRarity.value) {
      return false
    }
    if (item.tradable === false || item.floatValue === null || item.floatValue === undefined) {
      return false
    }
    if (item.categoryKey && !String(item.categoryKey).startsWith('weapon_')) {
      return false
    }
    if (selectedStatTrakType.value) {
      const itemType = isStatTrakItem(item) ? 'stattrak' : 'normal'
      if (itemType !== selectedStatTrakType.value && String(slot.inventoryAssetId || '') !== String(item.assetId)) {
        return false
      }
    }
    return true
  })
}

const formatFloat = (value) => {
  if (value === undefined || value === null || Number.isNaN(Number(value))) {
    return '--'
  }
  return Number(value).toFixed(8)
}

const slotStatusText = (slot) => {
  const selectedItem = slotInventoryItem(slot)
  if (slot.mode === 'inventory' && selectedItem) {
    return `库存 ${formatFloat(selectedItem.floatValue)}`
  }
  if (hasSlotValue(slot)) {
    return '手动锁定'
  }
  if (result.value?.reachable && result.value.requiredRemainingAverageFloat !== null) {
    return `建议 ${formatFloat(result.value.requiredRemainingAverageFloat)}`
  }
  return '自动计算'
}

const firstText = (...values) => values.find((value) => value !== undefined && value !== null && String(value).trim())
const displayItemName = (item) => firstText(
  item?.name,
  item?.shortName,
  item?.short_name,
  item?.raw?.name,
  item?.raw?.short_name,
  item?.raw?.goods_info?.name,
  item?.raw?.goods_info?.short_name,
  item?.raw?.market_hash_name
) || '未命名饰品'
const displayTargetName = (target) => firstText(
  target?.name,
  target?.shortName,
  target?.short_name,
  target?.raw?.name,
  target?.raw?.short_name,
  target?.raw?.market_hash_name
) || '未命名饰品'
const currency = (value) => `¥${Number(value || 0).toFixed(2)}`
const isStatTrakItem = (item) => /stattrak|暗金/i.test(`${displayItemName(item)} ${item?.raw?.market_hash_name || ''}`)
</script>

<template>
  <section class="page-panel reveal-up">
    <div class="float-calculator-grid">
      <section class="float-target-panel">
        <div class="section-head float-panel-head">
          <div>
            <span class="section-kicker">目标设置</span>
            <h2>特殊磨损计算器</h2>
          </div>
          <span class="float-contract-badge">{{ expectedContractSize }} 合 1</span>
        </div>
        <p class="surface-note">
          输入目标产物和目标磨损后，系统按产物自身 Min/Max Float 反推下级素材平均磨损。
        </p>

        <div class="float-form-grid">
          <label class="control-field">
            <span>
              收藏品
              <el-tooltip
                v-if="selectedRarity === 'gold'"
                content="刀和手套不属于任何收藏品，选择暗金档位时收藏品筛选不可用"
                placement="top"
              >
                <span class="field-hint">ⓘ 不适用</span>
              </el-tooltip>
            </span>
            <el-select
              v-model="selectedCollection"
              filterable
              clearable
              :disabled="selectedRarity === 'gold'"
              :placeholder="selectedRarity === 'gold' ? '刀 / 手套不区分收藏品' : '按收藏品筛选（可选）'"
              @change="onCollectionChange"
            >
              <el-option
                v-for="(col, idx) in collectionOptions"
                :key="idx"
                :label="col.zh || col.en"
                :value="col.zh || col.en"
              />
            </el-select>
          </label>

          <label class="control-field">
            <span>稀有度</span>
            <el-select
              v-model="selectedRarity"
              clearable
              placeholder="按稀有度筛选（可选）"
              @change="onRarityChange"
            >
              <el-option
                v-for="opt in rarityOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </label>

          <label class="control-field wide">
            <span>目标饰品</span>
            <el-select
              v-model="form.targetKey"
              filterable
              remote
              clearable
              reserve-keyword
              placeholder="按名称搜索产物"
              :remote-method="searchTargets"
              :loading="loadingTargets"
            >
              <el-option
                v-for="target in targetOptions"
                :key="target.key"
                :label="displayTargetName(target)"
                :value="target.key"
              >
                <span>{{ displayTargetName(target) }}</span>
                <small>{{ target.collection || '未知收藏品' }} · {{ formatFloat(target.minFloat) }} ~ {{ formatFloat(target.maxFloat) }}<template v-if="target.floatSource === 'library'"> · 基准库</template></small>
              </el-option>
            </el-select>
          </label>

          <label class="control-field">
            <span>目标磨损</span>
            <el-input-number
              v-model="form.targetFloat"
              :min="0"
              :max="1"
              :precision="8"
              :step="0.00000001"
              controls-position="right"
            />
          </label>

          <label class="control-field">
            <span>合成类型</span>
            <el-segmented v-model="form.contractSize" :options="contractSizeOptions" />
            <small>{{ expectedContractSize === 5 ? '隐秘 -> 金色特殊物品' : '常规档位 10 合 1' }}</small>
          </label>
        </div>

        <div v-if="selectedTarget" class="float-target-summary">
          <div class="float-target-summary-info">
            <strong>{{ displayTargetName(selectedTarget) }}</strong>
            <span>{{ selectedTarget.collection || '未知收藏品' }} · {{ selectedTarget.rarity || '未知档位' }}</span>
          </div>
          <em class="float-target-summary-range">Float {{ formatFloat(selectedTarget.minFloat) }} ~ {{ formatFloat(selectedTarget.maxFloat) }}</em>
        </div>

        <div class="float-rule-strip">
          <div class="float-rule-chip">
            <span class="float-rule-chip-label">反推公式</span>
            <strong>(目标 - Min) / (Max - Min)</strong>
          </div>
          <div class="float-rule-chip">
            <span class="float-rule-chip-label">槽位规则</span>
            <strong>{{ expectedContractSize === 5 ? '隐秘 → 金色 5 件' : '常规汰换 10 件' }}</strong>
          </div>
          <div class="float-rule-chip">
            <span class="float-rule-chip-label">锁定材料</span>
            <strong>实时重算剩余平均</strong>
          </div>
        </div>
      </section>

      <section class="float-result-panel" :class="{ reachable: result?.reachable, blocked: result && !result.reachable }">
        <div class="section-head float-result-head">
          <div>
            <span class="section-kicker">计算结果</span>
            <h2>{{ statusText }}</h2>
          </div>
          <div class="float-result-actions">
            <span class="float-state-pill" :class="{ reachable: result?.reachable, blocked: result && !result.reachable }">{{ statusText }}</span>
            <el-button type="primary" plain :loading="calculating" :disabled="!canCalculate" @click="calculate()">立即计算</el-button>
          </div>
        </div>
        <p v-if="result" class="surface-note" :class="{ 'result-msg-reachable': result.reachable, 'result-msg-blocked': !result.reachable }">{{ result.message }}</p>
        <p v-else-if="lastError" class="surface-note danger-note">{{ lastError }}</p>
        <p v-else class="surface-note">选择目标饰品并输入磨损后会自动计算。</p>

        <div class="float-metric-grid">
          <div class="float-metric-card">
            <label>所需平均</label>
            <strong>{{ formatFloat(result?.requiredAverageInputFloat) }}</strong>
          </div>
          <div class="float-metric-card">
            <label>所需总磨损</label>
            <strong>{{ formatFloat(result?.requiredTotalInputFloat) }}</strong>
          </div>
          <div class="float-metric-card">
            <label>已锁定总和</label>
            <strong>{{ formatFloat(result?.lockedFloatSum) }}</strong>
          </div>
          <div class="float-metric-card primary-metric">
            <label>剩余建议</label>
            <strong>{{ formatFloat(result?.requiredRemainingAverageFloat) }}</strong>
          </div>
        </div>

        <div class="float-bound-row">
          <span>剩余槽位 <b>{{ result?.remainingSlotCount ?? form.contractSize }}</b> / 已锁定 <b>{{ result?.lockedSlotCount ?? lockedCount }}</b></span>
          <span>单件允许 {{ formatFloat(result?.allowedRemainingMinFloat) }} ~ {{ formatFloat(result?.allowedRemainingMaxFloat) }}</span>
        </div>
        <div class="float-source-row">
          <span class="float-source-tag float-source-inv">库存 {{ inventoryLockedCount }}</span>
          <span class="float-source-tag float-source-manual">手填 {{ manualLockedCount }}</span>
          <span class="float-source-tag float-source-auto">自动 {{ automaticSlotCount }}</span>
        </div>
      </section>
    </div>

    <section class="float-material-panel">
      <div class="section-head float-material-head">
        <div>
          <span class="section-kicker">下级材料</span>
          <h2>锁定材料磨损</h2>
        </div>
        <div class="inline-actions">
          <el-button plain :disabled="!result?.reachable" @click="fillSuggestedFloats">填入建议</el-button>
          <el-button plain @click="resetSlots">清空材料</el-button>
        </div>
      </div>
      <p class="surface-note">
        每个槽位可选择当前账号库存，也可手动填写磨损；留空表示自动计算。{{ inventoryCandidateNote }}
      </p>

      <div class="float-slot-grid" :class="{ compact: form.contractSize === 5 }">
        <article
          v-for="(slot, index) in visibleSlots"
          :key="index"
          class="float-slot"
          :class="{ locked: hasSlotValue(slot) }"
        >
          <div class="float-slot-head">
            <span class="float-slot-badge">{{ index + 1 }}</span>
            <button v-if="hasSlotValue(slot)" type="button" class="float-slot-unlock" @click="clearSlot(slot)">
              <span>✕</span>
            </button>
          </div>
          <el-segmented
            v-model="slot.mode"
            :options="[
              { label: '库存', value: 'inventory' },
              { label: '手填', value: 'manual' },
            ]"
            size="small"
            @change="handleSlotModeChange(slot, $event)"
          />
          <el-select
            v-if="slot.mode === 'inventory'"
            v-model="slot.inventoryAssetId"
            filterable
            clearable
            :disabled="!hasInventorySnapshot"
            :loading="loadingInventoryCandidates"
            :placeholder="hasInventorySnapshot ? '选择库存饰品' : '暂无库存快照'"
          >
            <el-option
              v-for="item in candidateOptionsForSlot(slot)"
              :key="item.assetId"
              :label="`${displayItemName(item)} · ${formatFloat(item.floatValue)}`"
              :value="String(item.assetId)"
            >
              <span>{{ displayItemName(item) }}</span>
              <small>{{ formatFloat(item.floatValue) }} · {{ currency(item.price) }} · {{ item.qualityLabel || item.filterRarity || '未知档位' }}</small>
            </el-option>
          </el-select>
          <el-input-number
            v-else
            v-model="slot.manualFloat"
            :min="0"
            :max="1"
            :precision="8"
            :step="0.00000001"
            controls-position="right"
            placeholder="自动计算"
          />
          <div v-if="hasSlotValue(slot)" class="float-slot-bar-track">
            <div class="float-slot-bar-fill" :style="{ width: (slotFloat(slot) * 100).toFixed(1) + '%' }"></div>
          </div>
          <p v-if="slot.mode === 'inventory' && slotInventoryItem(slot)" class="float-slot-item-meta">
            {{ currency(slotInventoryItem(slot).price) }} · {{ slotInventoryItem(slot).collection || '未补全收藏品' }}
          </p>
          <span class="float-slot-status" :class="{ locked: hasSlotValue(slot), suggested: !hasSlotValue(slot) && result?.reachable }">{{ slotStatusText(slot) }}</span>
        </article>
      </div>
    </section>
  </section>
</template>

<style scoped>
/* ---- 目标面板 ---- */
.float-target-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 20px;
  border: 1px solid var(--line);
  background:
    radial-gradient(ellipse at 20% 0%, rgba(91, 124, 250, 0.08), transparent 320px),
    radial-gradient(ellipse at 80% 100%, rgba(143, 107, 255, 0.05), transparent 240px),
    rgba(23, 27, 34, 0.96);
}

/* ---- 结果面板 ---- */
.float-result-panel {
  position: relative;
  padding: 20px;
  border: 1px solid rgba(91, 124, 250, 0.28);
  border-left: 3px solid rgba(91, 124, 250, 0.42);
  background:
    linear-gradient(180deg, rgba(91, 124, 250, 0.06), rgba(23, 27, 34, 0) 140px),
    rgba(23, 27, 34, 0.96);
  transition: border-color 200ms ease, background 200ms ease;
}

.float-result-panel.reachable {
  border-color: rgba(91, 196, 138, 0.28);
  border-left-color: rgba(91, 196, 138, 0.5);
  background:
    linear-gradient(180deg, rgba(91, 196, 138, 0.06), rgba(23, 27, 34, 0) 140px),
    rgba(23, 27, 34, 0.96);
}

.float-result-panel.blocked {
  border-color: rgba(236, 106, 95, 0.28);
  border-left-color: rgba(236, 106, 95, 0.5);
  background:
    linear-gradient(180deg, rgba(236, 106, 95, 0.05), rgba(23, 27, 34, 0) 140px),
    rgba(23, 27, 34, 0.96);
}

/* ---- 材料面板 ---- */
.float-material-panel {
  padding: 20px;
  border: 1px solid var(--line);
  background: rgba(20, 24, 30, 0.96);
}

/* ---- 结果消息 ---- */
.result-msg-reachable {
  color: var(--good) !important;
}
.result-msg-blocked {
  color: var(--danger) !important;
}

/* ---- 目标摘要 ---- */
.float-target-summary {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-end;
  margin-top: 16px;
  padding: 14px 16px;
  border: 1px solid rgba(91, 124, 250, 0.32);
  border-radius: 4px;
  background:
    linear-gradient(90deg, rgba(91, 124, 250, 0.14), rgba(143, 107, 255, 0.06)),
    var(--surface-soft);
}
.float-target-summary-info strong,
.float-target-summary-info span {
  display: block;
}
.float-target-summary-info span {
  margin-top: 3px;
  color: var(--muted);
  font-size: 13px;
}
.float-target-summary-range {
  color: var(--accent);
  font-family: "Space Grotesk", sans-serif;
  font-size: 14px;
  font-style: normal;
  white-space: nowrap;
}

/* ---- 规则标签 ---- */
.float-rule-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: auto;
  padding-top: 16px;
}
.float-rule-chip {
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: rgba(17, 22, 29, 0.68);
}
.float-rule-chip-label {
  display: block;
  color: var(--muted);
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.float-rule-chip strong {
  display: block;
  margin-top: 5px;
  color: var(--text);
  font-size: 12px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

/* ---- 指标卡片 ---- */
.float-metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}
.float-metric-card {
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-top: 2px solid var(--line-strong);
  background: var(--surface-soft);
  border-radius: 4px;
}
.float-metric-card.primary-metric {
  border-color: rgba(91, 124, 250, 0.36);
  border-top-color: var(--accent);
  background: rgba(91, 124, 250, 0.1);
}
.float-metric-card label {
  color: var(--muted);
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.float-metric-card strong {
  display: block;
  margin-top: 6px;
  overflow-wrap: anywhere;
  font-family: "Space Grotesk", sans-serif;
  font-size: 18px;
  color: var(--text);
}
.float-metric-card.primary-metric strong {
  font-size: 22px;
  color: var(--accent-strong);
}

/* ---- 边界与来源行 ---- */
.float-bound-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}
.float-bound-row b {
  color: var(--text);
}
.float-source-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.float-source-tag {
  padding: 4px 10px;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: var(--surface-soft);
  color: var(--muted);
  font-size: 12px;
}
.float-source-tag.float-source-inv {
  border-color: rgba(91, 124, 250, 0.28);
  color: var(--accent);
}
.float-source-tag.float-source-manual {
  border-color: rgba(143, 107, 255, 0.28);
  color: var(--accent-strong);
}

/* ---- 状态标记 ---- */
.float-state-pill {
  display: inline-flex;
  min-height: 28px;
  align-items: center;
  justify-content: center;
  padding: 4px 12px;
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  background: var(--surface-soft);
  color: var(--muted);
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}
.float-state-pill.reachable {
  border-color: rgba(91, 196, 138, 0.44);
  background: rgba(91, 196, 138, 0.12);
  color: var(--good);
}
.float-state-pill.blocked {
  border-color: rgba(236, 106, 95, 0.44);
  background: rgba(236, 106, 95, 0.12);
  color: var(--danger);
}

/* ---- 合同标记 ---- */
.float-contract-badge {
  display: inline-flex;
  min-height: 28px;
  align-items: center;
  justify-content: center;
  padding: 5px 12px;
  border: 1px solid rgba(91, 124, 250, 0.36);
  border-radius: 4px;
  background: var(--accent-soft);
  color: var(--text);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

/* ---- 槽位网格 ---- */
.float-slot-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(190px, 1fr));
  gap: 10px;
  margin-top: 16px;
}
.float-slot-grid.compact {
  grid-template-columns: repeat(5, minmax(170px, 1fr));
}
.field-hint {
  margin-left: 6px;
  font-size: 12px;
  color: var(--text-muted, #8a93a3);
  cursor: help;
}

/* ---- 槽位卡片 ---- */
.float-slot {
  display: grid;
  gap: 8px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: rgba(17, 22, 29, 0.76);
  transition: border-color 180ms ease, background 180ms ease, box-shadow 180ms ease;
}
.float-slot.locked {
  border-color: rgba(91, 124, 250, 0.35);
  border-left: 3px solid var(--accent);
  background:
    linear-gradient(135deg, rgba(91, 124, 250, 0.08), rgba(143, 107, 255, 0.03)),
    rgba(20, 26, 36, 0.92);
  box-shadow: 0 1px 0 rgba(91, 124, 250, 0.08) inset;
}
.float-slot .el-segmented {
  --el-segmented-bg-color: var(--field-bg);
  --el-segmented-item-selected-bg-color: var(--surface-raised);
  --el-segmented-item-selected-color: var(--text);
  --el-segmented-item-hover-bg-color: var(--active-bg);
  --el-border-radius-base: 2px;
  width: 100%;
}
.float-slot .el-select {
  width: 100%;
}
.float-slot .el-select__wrapper,
.float-slot .el-input-number,
.float-slot .el-input-number .el-input__wrapper {
  min-height: 34px;
  height: 34px;
}

/* ---- 槽位头部 ---- */
.float-slot-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
  min-height: 22px;
}
.float-slot-badge {
  display: inline-grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  background: var(--surface-soft);
  color: var(--muted);
  font-size: 11px;
  font-weight: 700;
  font-family: "Space Grotesk", sans-serif;
  transition: border-color 180ms ease, background 180ms ease, color 180ms ease;
}
.float-slot.locked .float-slot-badge {
  border-color: rgba(91, 124, 250, 0.5);
  background: var(--accent-soft);
  color: var(--accent);
}
.float-slot-unlock {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 1px solid rgba(236, 106, 95, 0.35);
  border-radius: 50%;
  background: rgba(236, 106, 95, 0.1);
  color: var(--danger);
  font-size: 10px;
  cursor: pointer;
  transition: background 160ms ease, border-color 160ms ease;
}
.float-slot-unlock:hover {
  background: rgba(236, 106, 95, 0.22);
  border-color: rgba(236, 106, 95, 0.6);
}

/* ---- 磨损条 ---- */
.float-slot-bar-track {
  width: 100%;
  height: 4px;
  border-radius: 2px;
  background: var(--field-bg);
  overflow: hidden;
}
.float-slot-bar-fill {
  height: 100%;
  border-radius: 2px;
  background: linear-gradient(90deg, var(--accent), var(--accent-strong));
  min-width: 2px;
  transition: width 260ms ease;
}

/* ---- 槽位元信息 ---- */
.float-slot-item-meta {
  margin: -2px 0 0;
  overflow: hidden;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---- 槽位状态 ---- */
.float-slot-status {
  display: inline-flex;
  min-height: 24px;
  align-items: center;
  width: fit-content;
  padding: 3px 9px;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: var(--surface-soft);
  color: var(--muted);
  font-size: 12px;
  transition: border-color 180ms ease, background 180ms ease, color 180ms ease;
}
.float-slot-status.locked {
  border-color: rgba(91, 124, 250, 0.32);
  background: rgba(91, 124, 250, 0.08);
  color: var(--accent);
}
.float-slot-status.suggested {
  border-color: rgba(91, 196, 138, 0.28);
  background: rgba(91, 196, 138, 0.06);
  color: var(--good);
}

/* ---- 响应式 ---- */
@media (max-width: 1180px) {
  .float-slot-grid,
  .float-slot-grid.compact {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 860px) {
  .float-rule-strip {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 620px) {
  .float-slot-grid,
  .float-slot-grid.compact,
  .float-metric-grid {
    grid-template-columns: 1fr;
  }
  .float-result-head,
  .float-material-head,
  .float-bound-row {
    display: grid;
  }
}
</style>
