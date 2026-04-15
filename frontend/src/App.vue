<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import InventoryBoard from './components/InventoryBoard.vue'
import PlanWorkspace from './components/PlanWorkspace.vue'

const currency = (value) => {
  const number = Number(value || 0)
  return `¥${number.toFixed(2)}`
}

const percent = (value) => `${(Number(value || 0) * 100).toFixed(2)}%`

const loadingInventory = ref(false)
const loadingPlans = ref(false)
const selectedPlanIndex = ref(0)

const inventoryForm = reactive({
  outputPath: 'data/buff_inventory.json',
  inventoryPath: 'data/buff_inventory.json',
  game: 'csgo',
  pageSize: 80,
  maxPages: null,
})

const planForm = reactive({
  inventoryPath: 'data/buff_inventory.json',
  catalogPath: 'data/catalog.json',
  topK: 8,
  saleFeeRate: null,
  maxItemsPerRarity: null,
  maxCombinations: null,
})

const inventoryState = reactive({
  itemCount: 0,
  outputPath: '',
  items: [],
  lastAction: '尚未加载库存',
})

const planState = reactive({
  plans: [],
  lastAction: '尚未生成方案',
})

const inventoryStats = computed(() => {
  const items = inventoryState.items || []
  const totalCost = items.reduce((sum, item) => sum + Number(item.price || 0), 0)
  const tradableCount = items.filter((item) => item.tradable !== false).length
  const withFloatCount = items.filter((item) => item.floatValue !== null && item.floatValue !== undefined).length
  return [
    { label: '素材总数', value: String(items.length).padStart(2, '0') },
    { label: '可交易', value: String(tradableCount).padStart(2, '0') },
    { label: '有磨损值', value: String(withFloatCount).padStart(2, '0') },
    { label: '库存总成本', value: currency(totalCost) },
  ]
})

const groupedInventory = computed(() => {
  const groups = new Map()
  for (const item of inventoryState.items || []) {
    const key = item.name || '未命名饰品'
    if (!groups.has(key)) {
      groups.set(key, {
        name: key,
        count: 0,
        avgPrice: 0,
        minFloat: null,
        rarity: item.rarity || 'unknown',
        collection: item.collection || '未补全收藏品',
        items: [],
      })
    }
    const group = groups.get(key)
    group.count += 1
    group.items.push(item)
  }

  return Array.from(groups.values())
    .map((group) => {
      const totalPrice = group.items.reduce((sum, item) => sum + Number(item.price || 0), 0)
      const floats = group.items
        .map((item) => item.floatValue)
        .filter((value) => value !== null && value !== undefined)
      group.avgPrice = totalPrice / group.items.length
      group.minFloat = floats.length ? Math.min(...floats) : null
      return group
    })
    .sort((a, b) => b.count - a.count || a.avgPrice - b.avgPrice)
})

const sortedPlans = computed(() => {
  return [...(planState.plans || [])].sort((a, b) => {
    const byEv = Number(b.expectedOutputValue || 0) - Number(a.expectedOutputValue || 0)
    if (byEv !== 0) {
      return byEv
    }
    return Number(b.expectedProfit || 0) - Number(a.expectedProfit || 0)
  })
})

const selectedPlan = computed(() => sortedPlans.value[selectedPlanIndex.value] || null)

const summaryRibbon = computed(() => {
  const plan = selectedPlan.value
  if (!plan) {
    return [
      { label: 'Top EV', value: '--' },
      { label: '期望利润', value: '--' },
      { label: 'ROI', value: '--' },
    ]
  }
  return [
    { label: 'Top EV', value: currency(plan.expectedOutputValue) },
    { label: '期望利润', value: currency(plan.expectedProfit) },
    { label: 'ROI', value: percent(plan.roi) },
  ]
})

const postJson = async (url, payload) => {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || `Request failed: ${response.status}`)
  }
  return response.json()
}

const normalizeInventory = (payload, actionLabel) => {
  inventoryState.itemCount = payload.itemCount || 0
  inventoryState.outputPath = payload.outputPath || ''
  inventoryState.items = payload.items || []
  inventoryState.lastAction = actionLabel
  if (inventoryState.outputPath) {
    planForm.inventoryPath = inventoryState.outputPath
    inventoryForm.inventoryPath = inventoryState.outputPath
  }
}

const fetchInventory = async () => {
  loadingInventory.value = true
  try {
    const payload = await postJson('/api/buff/inventory/fetch', {
      outputPath: inventoryForm.outputPath,
      game: inventoryForm.game,
      pageSize: inventoryForm.pageSize,
      maxPages: inventoryForm.maxPages || null,
    })
    normalizeInventory(payload, '已从 BUFF 抓取并保存库存')
    ElMessage.success(`已同步 ${payload.itemCount} 件素材`)
  } catch (error) {
    ElMessage.error(error.message || '抓取库存失败')
  } finally {
    loadingInventory.value = false
  }
}

const loadInventory = async () => {
  loadingInventory.value = true
  try {
    const payload = await postJson('/api/buff/inventory/load', {
      inventoryPath: inventoryForm.inventoryPath,
    })
    normalizeInventory(payload, '已从本地文件载入库存')
    ElMessage.success(`已载入 ${payload.itemCount} 件素材`)
  } catch (error) {
    ElMessage.error(error.message || '读取库存失败')
  } finally {
    loadingInventory.value = false
  }
}

const optimizePlans = async () => {
  loadingPlans.value = true
  try {
    const payload = await postJson('/api/trade-up/optimize', {
      inventoryPath: planForm.inventoryPath,
      catalogPath: planForm.catalogPath,
      topK: planForm.topK,
      saleFeeRate: planForm.saleFeeRate,
      maxItemsPerRarity: planForm.maxItemsPerRarity,
      maxCombinations: planForm.maxCombinations,
    })
    planState.plans = payload.plans || []
    planState.lastAction = `已生成 ${planState.plans.length} 条推荐方案`
    selectedPlanIndex.value = 0
    ElMessage.success(`完成方案计算，共 ${planState.plans.length} 条`)
  } catch (error) {
    ElMessage.error(error.message || '生成方案失败')
  } finally {
    loadingPlans.value = false
  }
}
</script>

<template>
  <div class="workspace-shell">
    <div class="ambient ambient-a"></div>
    <div class="ambient ambient-b"></div>

    <header class="workspace-hero">
      <div class="hero-copy reveal-up">
        <p class="eyebrow">CS Trade-Up Workbench</p>
        <h1>库存看板与 EV 方案控制台</h1>
        <p class="hero-text">
          前端已拆分为独立项目，围绕库存状态、方案排序和单条合同细节构建一个克制但高信息密度的炼金工作台。
        </p>
      </div>
      <div class="hero-ribbon reveal-up reveal-delay">
        <div v-for="item in summaryRibbon" :key="item.label" class="ribbon-metric">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </div>
    </header>

    <main class="workspace-grid">
      <section class="control-strip reveal-up">
        <div class="control-block">
          <div class="section-head">
            <span class="section-kicker">BUFF Sync</span>
            <h2>库存采集</h2>
          </div>
          <el-form label-position="top" class="dense-form">
            <div class="field-grid">
              <el-form-item label="输出路径">
                <el-input v-model="inventoryForm.outputPath" />
              </el-form-item>
              <el-form-item label="读取路径">
                <el-input v-model="inventoryForm.inventoryPath" />
              </el-form-item>
              <el-form-item label="游戏">
                <el-input v-model="inventoryForm.game" />
              </el-form-item>
              <el-form-item label="分页大小">
                <el-input-number v-model="inventoryForm.pageSize" :min="1" :max="200" controls-position="right" />
              </el-form-item>
            </div>
            <div class="inline-actions">
              <el-button type="warning" :loading="loadingInventory" @click="fetchInventory">从 BUFF 抓取</el-button>
              <el-button plain :loading="loadingInventory" @click="loadInventory">从文件载入</el-button>
            </div>
          </el-form>
          <p class="surface-note">{{ inventoryState.lastAction }}</p>
        </div>

        <div class="control-block">
          <div class="section-head">
            <span class="section-kicker">Trade-Up Engine</span>
            <h2>方案计算</h2>
          </div>
          <el-form label-position="top" class="dense-form">
            <div class="field-grid">
              <el-form-item label="库存路径">
                <el-input v-model="planForm.inventoryPath" />
              </el-form-item>
              <el-form-item label="Catalog 路径">
                <el-input v-model="planForm.catalogPath" />
              </el-form-item>
              <el-form-item label="Top K">
                <el-input-number v-model="planForm.topK" :min="1" :max="30" controls-position="right" />
              </el-form-item>
            </div>
            <div class="inline-actions">
              <el-button type="primary" :loading="loadingPlans" @click="optimizePlans">生成推荐方案</el-button>
            </div>
          </el-form>
          <p class="surface-note">{{ planState.lastAction }}</p>
        </div>
      </section>

      <InventoryBoard :inventory-stats="inventoryStats" :grouped-inventory="groupedInventory" />

      <PlanWorkspace
        :plans="sortedPlans"
        :selected-plan="selectedPlan"
        :selected-plan-index="selectedPlanIndex"
        @select-plan="selectedPlanIndex = $event"
      />
    </main>
  </div>
</template>

