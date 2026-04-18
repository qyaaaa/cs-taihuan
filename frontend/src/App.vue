<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
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
const loadingSession = ref(false)
const selectedPlanIndex = ref(0)
const sessionDialogVisible = ref(false)

const sessionForm = reactive({
  cookie: '',
})

const sessionState = reactive({
  connected: false,
  valid: false,
  source: '',
  maskedCookie: '',
  updatedAt: '',
  lastValidatedAt: '',
  message: '尚未保存 BUFF 会话。',
})

const inventoryForm = reactive({
  outputPath: 'data/buff_inventory.json',
  inventoryPath: 'data/buff_inventory.json',
  game: 'csgo',
  pageSize: 80,
  maxPages: null,
  forceRefresh: true,
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

const planState = reactive({
  plans: [],
  lastAction: '尚未生成方案',
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

const inventoryItems = computed(() => {
  return inventoryState.items || []
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

const request = async (url, options = {}) => {
  const response = await fetch(url, options)
  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || `Request failed: ${response.status}`)
  }
  if (response.status === 204) {
    return null
  }
  return response.json()
}

const normalizeSession = (payload) => {
  sessionState.connected = Boolean(payload?.connected)
  sessionState.valid = Boolean(payload?.valid)
  sessionState.source = payload?.source || ''
  sessionState.maskedCookie = payload?.maskedCookie || ''
  sessionState.updatedAt = payload?.updatedAt || ''
  sessionState.lastValidatedAt = payload?.lastValidatedAt || ''
  sessionState.message = payload?.message || '尚未保存 BUFF 会话。'
}

const loadSessionStatus = async () => {
  loadingSession.value = true
  try {
    const payload = await request('/api/buff/session/status')
    normalizeSession(payload)
    if (payload?.valid) {
      await restorePersistedInventory()
    }
  } catch (error) {
    ElMessage.error(error.message || '读取登录状态失败')
  } finally {
    loadingSession.value = false
  }
}

const saveSession = async () => {
  loadingSession.value = true
  try {
    const payload = await postJson('/api/buff/session/import', {
      cookie: sessionForm.cookie,
      source: 'frontend-manual',
    })
    normalizeSession(payload)
    sessionDialogVisible.value = false
    sessionForm.cookie = ''
    ElMessage.success('BUFF 会话已保存到后端')
  } catch (error) {
    ElMessage.error(error.message || '保存会话失败')
  } finally {
    loadingSession.value = false
  }
}

const validateSession = async () => {
  loadingSession.value = true
  try {
    const payload = await postJson('/api/buff/session/validate', {})
    normalizeSession(payload)
    if (payload?.valid) {
      await restorePersistedInventory()
    }
    ElMessage.success(payload.message || '会话校验完成')
  } catch (error) {
    ElMessage.error(error.message || '会话校验失败')
  } finally {
    loadingSession.value = false
  }
}

const clearSession = async () => {
  loadingSession.value = true
  try {
    await request('/api/buff/session', { method: 'DELETE' })
    normalizeSession({
      connected: false,
      valid: false,
      source: '',
      maskedCookie: '',
      updatedAt: '',
      lastValidatedAt: '',
      message: '已清除后端托管的 BUFF 会话。',
    })
    ElMessage.success('已清除 BUFF 会话')
  } catch (error) {
    ElMessage.error(error.message || '清除会话失败')
  } finally {
    loadingSession.value = false
  }
}

const normalizeInventory = (payload, actionLabel) => {
  inventoryState.snapshotId = payload.snapshotId || null
  inventoryState.itemCount = payload.itemCount || 0
  inventoryState.outputPath = payload.outputPath || ''
  inventoryState.items = (payload.items || []).map(normalizeInventoryItem)
  inventoryState.usePersistedPaging = false
  inventoryState.lastAction = payload.message || actionLabel
  if (inventoryState.outputPath) {
    planForm.inventoryPath = inventoryState.outputPath
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
  const payload = await postJson('/api/buff/inventory/page', {
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
    const payload = await postJson('/api/buff/inventory/fetch', {
      outputPath: inventoryForm.outputPath,
      game: inventoryForm.game,
      pageSize: inventoryForm.pageSize,
      maxPages: inventoryForm.maxPages || null,
      forceRefresh: inventoryForm.forceRefresh,
    })
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
    const payload = await postJson('/api/buff/inventory/fetch/force', {
      outputPath: inventoryForm.outputPath,
      game: inventoryForm.game,
      pageSize: inventoryForm.pageSize,
      maxPages: inventoryForm.maxPages || null,
      forceRefresh: true,
    })
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

onMounted(() => {
  restorePersistedInventory().catch(() => {})
  loadSessionStatus()
})
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
              <span class="section-kicker">BUFF Session</span>
              <h2>登录中心</h2>
            </div>
            <div class="session-status-panel">
              <div class="session-chip-row">
                <span class="session-chip" :class="{ active: sessionState.connected }">
                  {{ sessionState.connected ? '已保存会话' : '未登录' }}
                </span>
                <span class="session-chip" :class="{ active: sessionState.valid }">
                  {{ sessionState.valid ? '会话有效' : '待校验' }}
                </span>
              </div>
              <strong class="session-mask">{{ sessionState.maskedCookie || '尚未导入 Cookie' }}</strong>
              <p class="surface-note">
                {{ sessionState.message }}
              </p>
              <p class="surface-note subtle-note">
                说明：BUFF 暂无稳定公开的第三方网页扫码接口，这一版先由前端管理后端托管会话，不再要求在 yml 中手配 session。
              </p>
              <div class="inline-actions">
                <el-button type="primary" :loading="loadingSession" @click="sessionDialogVisible = true">导入会话</el-button>
                <el-button plain :loading="loadingSession" @click="validateSession">校验会话</el-button>
                <el-button plain :loading="loadingSession" @click="clearSession">清除会话</el-button>
              </div>
            </div>
          </div>

          <div class="control-block">
            <div class="section-head">
              <span class="section-kicker">BUFF Sync</span>
              <h2>库存采集</h2>
            </div>
            <p class="surface-note">
              使用当前已验证的 BUFF 会话抓取最新库存，并自动回写本地 JSON 与数据库快照。
            </p>
            <p class="surface-note subtle-note">
              说明：BUFF 获取数据可能需要几分钟，分页抓取时系统会主动放慢请求节奏以降低限流概率。
            </p>
            <div class="inline-actions">
              <el-button type="warning" :loading="loadingInventory" @click="fetchInventory">从 BUFF 获取</el-button>
              <el-button plain :loading="loadingInventory" @click="forceFetchInventory">强制刷新</el-button>
            </div>
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

      <InventoryBoard
        :inventory-stats="inventoryStats"
        :inventory-items="inventoryItems"
        :current-page="inventoryState.currentPage"
        :page-size="inventoryState.pageSize"
        :total-items="inventoryState.usePersistedPaging ? inventoryState.totalItems : inventoryItems.length"
        :use-persisted-paging="inventoryState.usePersistedPaging"
        @page-change="changeInventoryPage"
      />

        <PlanWorkspace
          :plans="sortedPlans"
          :selected-plan="selectedPlan"
          :selected-plan-index="selectedPlanIndex"
          @select-plan="selectedPlanIndex = $event"
        />
      </main>

      <el-dialog
        v-model="sessionDialogVisible"
        title="导入 BUFF 会话"
        width="560px"
        class="session-dialog"
      >
        <p class="dialog-copy">
          先在浏览器登录 BUFF，然后把请求头里的完整 Cookie 粘贴到这里。保存后由后端托管，库存抓取会自动复用它。
        </p>
        <el-input
          v-model="sessionForm.cookie"
          type="textarea"
          :rows="8"
          placeholder="session=...; csrf_token=...; Device-Id=..."
        />
        <template #footer>
          <div class="dialog-actions">
            <el-button @click="sessionDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="loadingSession" @click="saveSession">保存会话</el-button>
          </div>
        </template>
      </el-dialog>
    </div>
</template>
