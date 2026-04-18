<script setup>
import { computed, watch, ref } from 'vue'

const props = defineProps({
  inventoryStats: {
    type: Array,
    required: true,
  },
  inventoryItems: {
    type: Array,
    required: true,
  },
  currentPage: {
    type: Number,
    default: 1,
  },
  pageSize: {
    type: Number,
    default: 50,
  },
  totalItems: {
    type: Number,
    default: 0,
  },
  usePersistedPaging: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['page-change'])

const localCurrentPage = ref(1)
const pageSize = computed(() => props.pageSize || 50)
const currentPage = computed({
  get() {
    return props.usePersistedPaging ? props.currentPage : localCurrentPage.value
  },
  set(value) {
    if (props.usePersistedPaging) {
      emit('page-change', value)
      return
    }
    localCurrentPage.value = value
  },
})

const pagedInventory = computed(() => {
  if (props.usePersistedPaging) {
    return props.inventoryItems
  }
  const start = (currentPage.value - 1) * pageSize.value
  return props.inventoryItems.slice(start, start + pageSize.value)
})

watch(
  () => props.inventoryItems.length,
  (length) => {
    if (props.usePersistedPaging) {
      return
    }
    const maxPage = Math.max(1, Math.ceil(length / pageSize.value))
    if (currentPage.value > maxPage) {
      currentPage.value = maxPage
    }
  }
)

const currency = (value) => `¥${Number(value || 0).toFixed(2)}`
const displayName = (item) => item?.name || item?.raw?.market_hash_name || '未命名饰品'
const imageSource = (item) => {
  return (
    item?.imageUrl
    || item?.raw?.original_icon_url
    || item?.raw?.icon_url
    || item?.raw?.asset_info?.info?.original_icon_url
    || item?.raw?.asset_info?.info?.icon_url
    || ''
  )
}
const floatText = (item) => {
  if (item?.floatValueRaw || item?.float_value_raw) {
    return item.floatValueRaw || item.float_value_raw
  }
  const floatValue = item?.floatValue ?? item?.float_value
  if (floatValue === null || floatValue === undefined) {
    return '--'
  }
  return Number(floatValue).toFixed(17).replace(/0+$/, '').replace(/\.$/, '')
}
const qualityText = (item) => item?.qualityLabel || item?.raw?.tags?.rarity?.localized_name || '未知品质'
const wearText = (item) => item?.wearName || item?.wear_name || '--'
</script>

<template>
  <section class="inventory-stage reveal-up">
    <div class="section-head">
      <div>
        <span class="section-kicker">Inventory Board</span>
        <h2>武器库存</h2>
      </div>
    </div>

    <div class="metrics-row">
      <div v-for="metric in inventoryStats" :key="metric.label" class="metric-tile">
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
      </div>
    </div>

    <div class="inventory-cards">
      <article v-for="item in pagedInventory" :key="item.assetId || item.goodsId || item.name" class="material-card">
        <img v-if="imageSource(item)" :src="imageSource(item)" :alt="displayName(item)" class="material-thumb" />
        <div class="material-topline">
          <span>{{ qualityText(item) }}</span>
          <strong>{{ currency(item.price) }}</strong>
        </div>
        <h3>{{ displayName(item) }}</h3>
        <p>{{ item.collection || '未补全收藏品' }}</p>
        <div class="material-meta">
          <label>磨损阶段</label>
          <strong>{{ wearText(item) }}</strong>
        </div>
        <div class="material-meta">
          <label>磨损度</label>
          <strong>{{ floatText(item) }}</strong>
        </div>
        <div class="material-meta">
          <label>是否可交易</label>
          <strong>{{ item.tradable === false ? '否' : '是' }}</strong>
        </div>
      </article>
    </div>

    <div v-if="totalItems > pageSize" class="inventory-pagination">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        layout="prev, pager, next, total"
        :total="totalItems"
        background
      />
    </div>
  </section>
</template>
