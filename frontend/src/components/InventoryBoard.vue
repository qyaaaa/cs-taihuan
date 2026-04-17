<script setup>
import { computed, ref, watch } from 'vue'

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

const inventoryView = ref('list')
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
  if (item?.floatValueRaw) {
    return item.floatValueRaw
  }
  if (item?.floatValue === null || item?.floatValue === undefined) {
    return '--'
  }
  return item.floatValue.toFixed(17).replace(/0+$/, '').replace(/\.$/, '')
}
const qualityText = (item) => item?.qualityLabel || item?.raw?.tags?.rarity?.localized_name || '未知品质'
</script>

<template>
  <section class="inventory-stage reveal-up">
    <div class="section-head section-head-row">
      <div>
        <span class="section-kicker">Inventory Board</span>
        <h2>炼金素材库存</h2>
      </div>
      <el-radio-group v-model="inventoryView" size="small" class="view-switch">
        <el-radio-button label="list">列表</el-radio-button>
        <el-radio-button label="card">卡片</el-radio-button>
      </el-radio-group>
    </div>

    <div class="metrics-row">
      <div v-for="metric in inventoryStats" :key="metric.label" class="metric-tile">
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
      </div>
    </div>

    <div v-if="inventoryView === 'list'" class="inventory-list">
      <div class="inventory-list-head">
        <span>饰品</span>
        <span>品质</span>
        <span>价格</span>
        <span>收藏品</span>
        <span>磨损度</span>
      </div>
      <button
        v-for="item in pagedInventory"
        :key="item.assetId || item.goodsId || item.name"
        class="inventory-row"
        type="button"
      >
        <span class="inventory-item-main">
          <img v-if="imageSource(item)" :src="imageSource(item)" :alt="displayName(item)" class="inventory-thumb" />
          <strong>{{ displayName(item) }}</strong>
          <em>{{ item.wearName || '未标注磨损阶段' }}</em>
        </span>
        <span>{{ qualityText(item) }}</span>
        <span>{{ currency(item.price) }}</span>
        <span>{{ item.collection || '未补全收藏品' }}</span>
        <span>{{ floatText(item) }}</span>
      </button>
    </div>

    <div v-else class="inventory-cards">
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
          <strong>{{ item.wearName || '--' }}</strong>
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
