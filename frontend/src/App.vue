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
const loadingNextTier = ref(false)
const loadingCatalog = ref(false)
const loadingSession = ref(false)
const selectedPlanIndex = ref(0)
const sessionDialogVisible = ref(false)
const activePage = ref('overview')

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
  nextTierAction: '尚未保存关联档位冗余数据',
  catalogAction: '尚未同步 Catalog 数据',
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

const navItems = computed(() => [
  {
    key: 'overview',
    label: '总览',
    metric: inventoryState.snapshotId ? `#${inventoryState.snapshotId}` : '未载入',
  },
  {
    key: 'inventory',
    label: '库存',
    metric: `${inventoryStats.value[0]?.value || '00'} 件`,
  },
  {
    key: 'plans',
    label: '方案',
    metric: `${sortedPlans.value.length} 条`,
  },
  {
    key: 'data',
    label: '数据',
    metric: sessionState.connected ? '已连接' : '未登录',
  },
])

const pageMeta = computed(() => {
  const meta = {
    overview: {
      kicker: 'Workbench',
      title: '工作总览',
      description: '查看会话、库存、Catalog 和方案状态，并从这里进入下一步操作。',
    },
    inventory: {
      kicker: 'Inventory',
      title: '炼金素材库存',
      description: '展示数据库中最近一次保存的武器库存，保留每一件独立饰品和完整磨损信息。',
    },
    plans: {
      kicker: 'Trade-Up',
      title: 'EV 推荐方案',
      description: '按期望价值查看推荐合同，选中方案后核对输入素材和潜在产出。',
    },
    data: {
      kicker: 'Data Ops',
      title: '会话与数据维护',
      description: '维护 BUFF 会话、同步 Catalog，并保存关联档位冗余数据。',
    },
  }
  return meta[activePage.value] || meta.overview
})

const statusCards = computed(() => [
  {
    label: 'BUFF 会话',
    value: sessionState.connected ? (sessionState.valid ? '有效' : '待校验') : '未登录',
    note: sessionState.maskedCookie || '尚未导入 Cookie',
    target: 'data',
  },
  {
    label: '库存快照',
    value: inventoryState.snapshotId ? `#${inventoryState.snapshotId}` : '--',
    note: inventoryState.lastAction,
    target: 'inventory',
  },
  {
    label: 'Catalog',
    value: loadingCatalog.value ? '同步中' : '就绪',
    note: planState.catalogAction,
    target: 'data',
  },
  {
    label: '推荐方案',
    value: `${sortedPlans.value.length} 条`,
    note: planState.lastAction,
    target: 'plans',
  },
])

const parseErrorText = (text, status) => {
  const normalized = String(text || '').trim()
  if (!normalized) {
    return `Request failed: ${status}`
  }
  try {
    const payload = JSON.parse(normalized)
    if (payload?.message) {
      return payload.message
    }
  } catch (error) {
    // Ignore JSON parse failures and fall back to the raw response text.
  }
  return normalized
}

const postJson = async (url, payload) => {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!response.ok) {
    const text = await response.text()
    throw new Error(parseErrorText(text, response.status))
  }
  return response.json()
}

const request = async (url, options = {}) => {
  const response = await fetch(url, options)
  if (!response.ok) {
    const text = await response.text()
    throw new Error(parseErrorText(text, response.status))
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

const syncCatalog = async () => {
  loadingCatalog.value = true
  try {
    const payload = await postJson('/api/catalog/sync', {
      snapshotId: inventoryState.snapshotId,
    })
    planState.catalogAction = payload.message || `已同步 ${payload.itemCount} 条 Catalog 数据`
    ElMessage.success(payload.message || `已同步 ${payload.itemCount} 条 Catalog 数据`)
  } catch (error) {
    const message = String(error.message || '同步 Catalog 失败')
    ElMessage.error(message || '同步 Catalog 失败')
  } finally {
    loadingCatalog.value = false
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
      snapshotId: inventoryState.snapshotId,
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
    const message = String(error.message || '生成方案失败')
    if (message.includes('Catalog 数据库为空')) {
      ElMessage.error('Catalog 数据库为空，请先点击“从 BUFF 同步 Catalog”')
    } else {
      ElMessage.error(message || '生成方案失败')
    }
  } finally {
    loadingPlans.value = false
  }
}

const persistNextTierCatalog = async () => {
  loadingNextTier.value = true
  try {
    const payload = await postJson('/api/trade-up/next-tier/persist', {
      snapshotId: inventoryState.snapshotId,
    })
    planState.nextTierAction = payload.message || `已保存 ${payload.itemCount} 条关联档位冗余数据`
    ElMessage.success(payload.message || `已保存 ${payload.itemCount} 条关联档位冗余数据`)
  } catch (error) {
    const message = String(error.message || '保存关联档位冗余数据失败')
    if (message.includes('Catalog 数据库为空')) {
      ElMessage.error('Catalog 数据库为空，请先点击“从 BUFF 同步 Catalog”')
    } else {
      ElMessage.error(message || '保存关联档位冗余数据失败')
    }
  } finally {
    loadingNextTier.value = false
  }
}

onMounted(() => {
  restorePersistedInventory().catch(() => {})
  loadSessionStatus()
})
</script>

<template>
  <div class="workspace-shell">
    <aside class="workspace-sidebar">
      <div class="brand-lockup">
        <span>CT</span>
        <div>
          <strong>CS 汰换</strong>
          <small>Trade-Up Console</small>
        </div>
      </div>

      <nav class="workspace-nav" aria-label="主导航">
        <button
          v-for="item in navItems"
          :key="item.key"
          type="button"
          class="nav-item"
          :class="{ active: activePage === item.key }"
          @click="activePage = item.key"
        >
          <span>{{ item.label }}</span>
          <small>{{ item.metric }}</small>
        </button>
      </nav>
    </aside>

    <div class="workspace-main">
      <header class="workspace-topbar reveal-up">
        <div>
          <p class="eyebrow">{{ pageMeta.kicker }}</p>
          <h1>{{ pageMeta.title }}</h1>
          <p class="page-description">{{ pageMeta.description }}</p>
        </div>
        <div class="session-compact" :class="{ active: sessionState.connected }">
          <span>{{ sessionState.connected ? 'BUFF 已托管' : 'BUFF 未登录' }}</span>
          <strong>{{ sessionState.maskedCookie || 'No session' }}</strong>
        </div>
      </header>

      <main class="page-stack">
        <section v-if="activePage === 'overview'" class="page-panel reveal-up">
          <div class="overview-grid">
            <button
              v-for="card in statusCards"
              :key="card.label"
              type="button"
              class="status-card"
              @click="activePage = card.target"
            >
              <span>{{ card.label }}</span>
              <strong>{{ card.value }}</strong>
              <small>{{ card.note }}</small>
            </button>
          </div>

          <div class="overview-split">
            <section class="operation-panel">
              <div class="section-head">
                <span class="section-kicker">Next Action</span>
                <h2>常用操作</h2>
              </div>
              <div class="action-list">
                <button type="button" class="action-row" @click="activePage = 'data'; sessionDialogVisible = true">
                  <strong>导入 BUFF 会话</strong>
                  <span>保存 Cookie 后，后端会托管后续抓取请求。</span>
                </button>
                <button type="button" class="action-row" @click="activePage = 'inventory'; fetchInventory()">
                  <strong>从 BUFF 获取库存</strong>
                  <span>抓取过程可能持续几分钟，分页请求会主动等待。</span>
                </button>
                <button type="button" class="action-row" @click="activePage = 'plans'; optimizePlans()">
                  <strong>生成推荐方案</strong>
                  <span>读取数据库库存和 catalog 数据，按 EV 排序。</span>
                </button>
              </div>
            </section>

            <section class="operation-panel">
              <div class="section-head">
                <span class="section-kicker">Best Plan</span>
                <h2>方案摘要</h2>
              </div>
              <div class="hero-ribbon">
                <div v-for="item in summaryRibbon" :key="item.label" class="ribbon-metric">
                  <span>{{ item.label }}</span>
                  <strong>{{ item.value }}</strong>
                </div>
              </div>
            </section>
          </div>
        </section>

        <section v-if="activePage === 'inventory'" class="page-panel reveal-up">
          <div class="page-toolbar">
            <div>
              <span class="section-kicker">BUFF Sync</span>
              <h2>库存采集</h2>
              <p class="surface-note">使用当前 BUFF 会话抓取最新库存，并写入数据库快照。</p>
              <p class="surface-note subtle-note">BUFF 获取数据可能需要几分钟，分页抓取时系统会主动放慢请求节奏。</p>
            </div>
            <div class="inline-actions">
              <el-button type="warning" :loading="loadingInventory" @click="fetchInventory">从 BUFF 获取</el-button>
              <el-button plain :loading="loadingInventory" @click="forceFetchInventory">强制刷新</el-button>
            </div>
          </div>
          <p class="surface-note toolbar-note">{{ inventoryState.lastAction }}</p>

          <InventoryBoard
            :inventory-stats="inventoryStats"
            :inventory-items="inventoryItems"
            :current-page="inventoryState.currentPage"
            :page-size="inventoryState.pageSize"
            :total-items="inventoryState.usePersistedPaging ? inventoryState.totalItems : inventoryItems.length"
            :use-persisted-paging="inventoryState.usePersistedPaging"
            @page-change="changeInventoryPage"
          />
        </section>

        <section v-if="activePage === 'plans'" class="page-panel reveal-up">
          <div class="page-toolbar">
            <div>
              <span class="section-kicker">Trade-Up Engine</span>
              <h2>方案计算</h2>
              <p class="surface-note">方案计算读取数据库里最近一次保存的武器库存快照，并使用数据库中的 catalog 数据。</p>
            </div>
            <el-form label-position="top" class="plan-toolbar-form">
              <el-form-item label="Top K">
                <el-input-number v-model="planForm.topK" :min="1" :max="30" controls-position="right" />
              </el-form-item>
              <el-button type="primary" :loading="loadingPlans" @click="optimizePlans">生成推荐方案</el-button>
            </el-form>
          </div>
          <p class="surface-note toolbar-note">{{ planState.lastAction }}</p>

          <PlanWorkspace
            :plans="sortedPlans"
            :selected-plan="selectedPlan"
            :selected-plan-index="selectedPlanIndex"
            @select-plan="selectedPlanIndex = $event"
          />
        </section>

        <section v-if="activePage === 'data'" class="page-panel reveal-up">
          <div class="data-grid">
            <section class="operation-panel">
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
                <p class="surface-note">{{ sessionState.message }}</p>
                <p class="surface-note subtle-note">
                  BUFF 暂无稳定公开的第三方网页扫码接口，这一版先由前端管理后端托管会话，不再要求在 yml 中手配 session。
                </p>
                <div class="inline-actions">
                  <el-button type="primary" :loading="loadingSession" @click="sessionDialogVisible = true">导入会话</el-button>
                  <el-button plain :loading="loadingSession" @click="validateSession">校验会话</el-button>
                  <el-button plain :loading="loadingSession" @click="clearSession">清除会话</el-button>
                </div>
              </div>
            </section>

            <section class="operation-panel">
              <div class="section-head">
                <span class="section-kicker">Catalog</span>
                <h2>目录同步</h2>
              </div>
              <p class="surface-note">
                Catalog 同步会按当前库存快照里的 goods_id 分批补抓 BUFF 市场详情。每个 goods 请求之间都会主动等待几秒，以降低限流概率。
              </p>
              <div class="inline-actions">
                <el-button plain :loading="loadingCatalog" @click="syncCatalog">从 BUFF 同步 Catalog</el-button>
                <el-button plain :loading="loadingNextTier" @click="persistNextTierCatalog">保存关联档位数据</el-button>
              </div>
              <p class="surface-note">{{ planState.catalogAction }}</p>
              <p class="surface-note">{{ planState.nextTierAction }}</p>
            </section>
          </div>
        </section>
      </main>
    </div>

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
