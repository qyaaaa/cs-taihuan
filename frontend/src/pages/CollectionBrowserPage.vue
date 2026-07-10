<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listSkinCollectionsApi } from '../api/skinFloatRange'

const RARITY_META = {
  consumer: { label: '消费级', color: '#8f9aaa', order: 1 },
  industrial: { label: '工业级', color: '#5aa3ff', order: 2 },
  'mil-spec': { label: '军规级', color: '#4d6bff', order: 3 },
  restricted: { label: '受限', color: '#8f6bff', order: 4 },
  classified: { label: '保密', color: '#d85cff', order: 5 },
  covert: { label: '隐秘', color: '#ef5b69', order: 6 },
  gold: { label: '金色', color: '#f6c453', order: 7 },
}

const loading = ref(false)
const collections = ref([])
const selectedKey = ref('')
const collectionKeyword = ref('')
const itemKeyword = ref('')
const rarityFilter = ref('all')

const selectedCollection = computed(() => {
  return collections.value.find((collection) => collection.key === selectedKey.value) || filteredCollections.value[0] || null
})

const filteredCollections = computed(() => {
  const keyword = collectionKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return collections.value
  }
  return collections.value.filter((collection) => {
    return `${collection.nameZh || ''} ${collection.nameEn || ''}`.toLowerCase().includes(keyword)
  })
})

const visibleItems = computed(() => {
  const keyword = itemKeyword.value.trim().toLowerCase()
  return normalizeItems(selectedCollection.value?.items || [])
    .filter((item) => rarityFilter.value === 'all' || item.rarity === rarityFilter.value)
    .filter((item) => {
      if (!keyword) {
        return true
      }
      return `${item.nameZh || ''} ${item.nameEn || ''} ${item.weapon || ''}`.toLowerCase().includes(keyword)
    })
})

const totalItemCount = computed(() => collections.value.reduce((sum, collection) => sum + Number(collection.itemCount || 0), 0))
const maxCollectionSize = computed(() => Math.max(...collections.value.map((collection) => Number(collection.itemCount || 0)), 1))
const rarityOptions = computed(() => {
  const seen = new Set()
  collections.value.forEach((collection) => {
    ;(collection.rarities || []).forEach((rarity) => seen.add(rarity))
  })
  return [...seen]
    .filter(Boolean)
    .sort((left, right) => rarityOrder(left) - rarityOrder(right))
    .map((rarity) => ({ value: rarity, label: rarityLabel(rarity) }))
})
const selectedRarityCounts = computed(() => {
  const counts = new Map()
  ;(selectedCollection.value?.items || []).forEach((item) => {
    counts.set(item.rarity, (counts.get(item.rarity) || 0) + 1)
  })
  return [...counts.entries()]
    .sort(([left], [right]) => rarityOrder(right) - rarityOrder(left))
    .map(([rarity, count]) => ({ rarity, count, label: rarityLabel(rarity), color: rarityColor(rarity) }))
})

const loadCollections = async () => {
  loading.value = true
  try {
    const payload = await listSkinCollectionsApi()
    collections.value = (payload || []).map(normalizeCollection)
    selectedKey.value = collections.value[0]?.key || ''
  } catch (error) {
    collections.value = []
    ElMessage.error(error.message || '加载收藏品图鉴失败')
  } finally {
    loading.value = false
  }
}

const selectCollection = (collection) => {
  selectedKey.value = collection.key
  itemKeyword.value = ''
  rarityFilter.value = 'all'
}

const normalizeCollection = (collection) => ({
  ...collection,
  key: collection.key || collection.nameZh || collection.nameEn || '',
  nameZh: collection.nameZh || collection.name_zh || '',
  nameEn: collection.nameEn || collection.name_en || '',
  itemCount: Number(collection.itemCount || collection.item_count || (collection.items || []).length || 0),
  rarities: collection.rarities || [],
  releaseDate: collection.releaseDate || collection.release_date || '',
  items: collection.items || [],
})

const normalizeItems = (items) => {
  return items
    .map((item) => ({
      ...item,
      skinId: item.skinId || item.skin_id || '',
      paintIndex: item.paintIndex || item.paint_index || '',
      nameZh: item.nameZh || item.name_zh || '',
      nameEn: item.nameEn || item.name_en || '',
      weapon: item.weapon || '',
      image: item.image || item.image_url || '',
      rarity: item.rarity || '',
      minFloat: numberValue(item.minFloat, item.min_float),
      maxFloat: numberValue(item.maxFloat, item.max_float),
    }))
    .sort((left, right) => {
      // 稀有度越高越靠前（金 > 隐秘 > 保密 > ... > 消费级）。
      const byRarity = rarityOrder(right.rarity) - rarityOrder(left.rarity)
      if (byRarity !== 0) {
        return byRarity
      }
      return displaySkinName(left).localeCompare(displaySkinName(right))
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
const displayCollectionName = (collection) => collection?.nameZh || collection?.nameEn || '未命名收藏品'
const displayCollectionSub = (collection) => collection?.nameEn && collection?.nameEn !== collection?.nameZh ? collection.nameEn : 'Collection'
const displaySkinName = (item) => item?.nameZh || item?.nameEn || '未命名皮肤'
const displaySkinSub = (item) => item?.nameEn && item?.nameEn !== item?.nameZh ? item.nameEn : item?.weapon || 'Skin'
const rarityLabel = (rarity) => RARITY_META[rarity]?.label || rarity || '未知'
const rarityColor = (rarity) => RARITY_META[rarity]?.color || '#edf1f7'
const rarityOrder = (rarity) => RARITY_META[rarity]?.order || 99
const formatFloat = (value) => Number(value || 0).toFixed(6)
const floatRangeWidth = (item) => `${Math.max(3, (Number(item.maxFloat || 0) - Number(item.minFloat || 0)) * 100)}%`
const floatRangeLeft = (item) => `${Math.min(100, Math.max(0, Number(item.minFloat || 0) * 100))}%`
const collectionBarWidth = (collection) => `${Math.max(4, Math.round((Number(collection.itemCount || 0) / maxCollectionSize.value) * 100))}%`

onMounted(loadCollections)
</script>

<template>
  <section class="collection-browser page-panel reveal-up">
    <div class="collection-toolbar operation-panel">
      <div>
        <span class="section-kicker">收藏品图鉴</span>
        <h2>收藏品与磨损范围</h2>
        <p class="surface-note">按收藏品查看当前基准库中的所有产物，以及每个皮肤自身的 Min / Max Float。</p>
      </div>
      <div class="collection-summary">
        <span>{{ collections.length }} 个收藏品</span>
        <strong>{{ totalItemCount }} 件产物</strong>
      </div>
    </div>

    <div class="collection-browser-grid">
      <aside class="collection-list-panel operation-panel">
        <div class="collection-filter-row">
          <el-input v-model="collectionKeyword" clearable placeholder="搜索收藏品" />
        </div>
        <div class="collection-list-scroll">
          <div v-if="loading" class="collection-empty">正在加载收藏品...</div>
          <div v-else-if="!filteredCollections.length" class="collection-empty">没有匹配的收藏品</div>
          <template v-else>
            <button
              v-for="collection in filteredCollections"
              :key="collection.key"
              type="button"
              class="collection-row"
              :class="{ active: selectedCollection?.key === collection.key }"
              @click="selectCollection(collection)"
            >
              <span>
                <strong>{{ displayCollectionName(collection) }}</strong>
                <small>{{ collection.releaseDate ? `上线 ${collection.releaseDate}` : displayCollectionSub(collection) }}</small>
              </span>
              <em>{{ collection.itemCount }} 件</em>
              <i :style="{ width: collectionBarWidth(collection) }"></i>
            </button>
          </template>
        </div>
      </aside>

      <section class="collection-detail-panel operation-panel">
        <div v-if="selectedCollection" class="collection-detail-head">
          <div>
            <span class="section-kicker">{{ displayCollectionSub(selectedCollection) }}</span>
            <h2>{{ displayCollectionName(selectedCollection) }}</h2>
            <p class="surface-note">
              {{ selectedCollection.itemCount }} 件产物，当前展示 {{ visibleItems.length }} 件。
              <template v-if="selectedCollection.releaseDate">上线时间 {{ selectedCollection.releaseDate }}。</template>
            </p>
          </div>
          <div class="collection-rarity-strip">
            <span
              v-for="item in selectedRarityCounts"
              :key="item.rarity"
              :style="{ borderColor: item.color, color: item.color }"
            >
              {{ item.label }} {{ item.count }}
            </span>
          </div>
        </div>

        <div class="collection-item-toolbar">
          <el-input v-model="itemKeyword" clearable placeholder="搜索产物 / 武器" />
          <el-select v-model="rarityFilter" placeholder="档位" class="collection-rarity-select">
            <el-option label="全部档位" value="all" />
            <el-option v-for="option in rarityOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </div>

        <div class="collection-item-table">
          <div class="collection-table-head">
            <span>产物</span>
            <span>品质</span>
            <span>磨损范围</span>
            <span>图案编号</span>
          </div>
          <article v-for="item in visibleItems" :key="item.skinId || `${item.nameEn}-${item.paintIndex}`" class="collection-item-row">
            <div class="skin-name-cell">
              <img
                v-if="item.image"
                :src="item.image"
                :alt="displaySkinName(item)"
                class="skin-thumb"
                loading="lazy"
                referrerpolicy="no-referrer"
              />
              <span class="skin-thumb skin-thumb-empty" v-else></span>
              <span class="skin-name-text">
                <strong>{{ displaySkinName(item) }}</strong>
                <small>{{ displaySkinSub(item) }}</small>
              </span>
            </div>
            <span class="collection-rarity-pill" :style="{ borderColor: rarityColor(item.rarity), color: rarityColor(item.rarity) }">
              {{ rarityLabel(item.rarity) }}
            </span>
            <div class="float-range-cell">
              <div class="float-range-track">
                <i :style="{ left: floatRangeLeft(item), width: floatRangeWidth(item), background: rarityColor(item.rarity) }"></i>
              </div>
              <small>{{ formatFloat(item.minFloat) }} - {{ formatFloat(item.maxFloat) }}</small>
            </div>
            <span class="paint-index">{{ item.paintIndex || '--' }}</span>
          </article>
          <div v-if="!loading && !visibleItems.length" class="collection-empty">没有匹配的产物</div>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.skin-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.skin-thumb {
  flex: 0 0 auto;
  width: 56px;
  height: 42px;
  object-fit: contain;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.04);
}
.skin-thumb-empty {
  display: inline-block;
}
.skin-name-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.skin-name-text small {
  opacity: 0.6;
}
</style>
