<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { loadInventoryPage } from '../api/inventory'
import { calculateFloatApi, searchFloatTargetsApi } from '../api/tradeUp'

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
  targetGoodsId: '',
  targetFloat: 0.05201314,
  contractSize: 10,
  slots: Array.from({ length: 10 }, createSlot),
})

const visibleSlots = computed(() => form.slots.slice(0, form.contractSize))
const selectedTarget = computed(() => targetOptions.value.find((item) => item.goodsId === form.targetGoodsId) || null)
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
const canCalculate = computed(() => Boolean(form.targetGoodsId && form.targetFloat !== null && form.targetFloat !== ''))
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

const searchTargets = async (keyword = '') => {
  loadingTargets.value = true
  try {
    targetOptions.value = (await searchFloatTargetsApi(keyword, props.accountId)).map(normalizeTarget)
  } catch (error) {
    ElMessage.error(error.message || '加载目标饰品失败')
  } finally {
    loadingTargets.value = false
  }
}

const loadInventoryCandidates = async () => {
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
    ElMessage.warning(error.message || '库存候选加载失败，可先手动填写磨损')
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
      targetGoodsId: form.targetGoodsId,
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
    form.targetGoodsId,
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
    searchTargets()
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
  searchTargets()
  loadInventoryCandidates()
})

const normalizeTarget = (target) => ({
  ...target,
  goodsId: target.goodsId || target.goods_id,
  rarity: target.rarity,
  minFloat: numberValue(target.minFloat, target.min_float),
  maxFloat: numberValue(target.maxFloat, target.max_float),
})

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
      <section class="operation-panel float-target-panel">
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
          <label class="control-field wide">
            <span>目标饰品</span>
            <el-select
              v-model="form.targetGoodsId"
              filterable
              remote
              clearable
              reserve-keyword
              placeholder="搜索名称 / goods_id / 收藏品"
              :remote-method="searchTargets"
              :loading="loadingTargets"
            >
              <el-option
                v-for="target in targetOptions"
                :key="target.goodsId"
                :label="displayTargetName(target)"
                :value="target.goodsId"
              >
                <span>{{ displayTargetName(target) }}</span>
                <small>{{ target.collection }} · {{ formatFloat(target.minFloat) }} ~ {{ formatFloat(target.maxFloat) }}</small>
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
          <div>
            <strong>{{ displayTargetName(selectedTarget) }}</strong>
            <span>{{ selectedTarget.collection || '未知收藏品' }} · {{ selectedTarget.rarity || '未知档位' }}</span>
          </div>
          <em>Float {{ formatFloat(selectedTarget.minFloat) }} ~ {{ formatFloat(selectedTarget.maxFloat) }}</em>
        </div>

        <div class="float-rule-strip">
          <div>
            <span>反推公式</span>
            <strong>(目标磨损 - Min) / (Max - Min)</strong>
          </div>
          <div>
            <span>槽位规则</span>
            <strong>{{ expectedContractSize === 5 ? '隐秘到金色 5 件' : '常规汰换 10 件' }}</strong>
          </div>
          <div>
            <span>锁定材料</span>
            <strong>实时重算剩余平均</strong>
          </div>
        </div>
      </section>

      <section class="operation-panel float-result-panel" :class="{ reachable: result?.reachable, blocked: result && !result.reachable }">
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
        <p v-if="result" class="surface-note">{{ result.message }}</p>
        <p v-else-if="lastError" class="surface-note danger-note">{{ lastError }}</p>
        <p v-else class="surface-note">选择目标饰品并输入磨损后会自动计算。</p>

        <div class="float-metric-grid">
          <div>
            <label>所需平均</label>
            <strong>{{ formatFloat(result?.requiredAverageInputFloat) }}</strong>
          </div>
          <div>
            <label>所需总磨损</label>
            <strong>{{ formatFloat(result?.requiredTotalInputFloat) }}</strong>
          </div>
          <div>
            <label>已锁定总和</label>
            <strong>{{ formatFloat(result?.lockedFloatSum) }}</strong>
          </div>
          <div class="primary-metric">
            <label>剩余建议</label>
            <strong>{{ formatFloat(result?.requiredRemainingAverageFloat) }}</strong>
          </div>
        </div>

        <div class="float-bound-row">
          <span>剩余槽位：{{ result?.remainingSlotCount ?? form.contractSize }} / 已锁定：{{ result?.lockedSlotCount ?? lockedCount }}</span>
          <span>单件允许：{{ formatFloat(result?.allowedRemainingMinFloat) }} ~ {{ formatFloat(result?.allowedRemainingMaxFloat) }}</span>
        </div>
        <div class="float-source-row">
          <span>库存 {{ inventoryLockedCount }}</span>
          <span>手填 {{ manualLockedCount }}</span>
          <span>自动 {{ automaticSlotCount }}</span>
        </div>
      </section>
    </div>

    <section class="operation-panel">
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
        每个槽位可选择当前账号库存，也可手动填写磨损；留空表示自动计算。库存候选会按目标下级档位、可交易、武器皮肤和已选 StatTrak 类型过滤。
      </p>

      <div class="float-slot-grid" :class="{ compact: form.contractSize === 5 }">
        <article
          v-for="(slot, index) in visibleSlots"
          :key="index"
          class="float-slot"
          :class="{ locked: hasSlotValue(slot) }"
        >
          <div class="float-slot-head">
            <strong>#{{ String(index + 1).padStart(2, '0') }}</strong>
            <button v-if="hasSlotValue(slot)" type="button" class="inline-link-button" @click="clearSlot(slot)">取消锁定</button>
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
            :loading="loadingInventoryCandidates"
            placeholder="选择库存饰品"
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
          <p v-if="slot.mode === 'inventory' && slotInventoryItem(slot)" class="float-slot-item-meta">
            {{ currency(slotInventoryItem(slot).price) }} · {{ slotInventoryItem(slot).collection || '未补全收藏品' }}
          </p>
          <span>{{ slotStatusText(slot) }}</span>
        </article>
      </div>
    </section>
  </section>
</template>
