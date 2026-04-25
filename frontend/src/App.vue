<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import InventoryBoard from './components/InventoryBoard.vue'
import PlanWorkspace from './components/PlanWorkspace.vue'

const currency = (value) => {
  const number = Number(value || 0)
  return `¥${number.toFixed(2)}`
}

const percent = (value) => `${(Number(value || 0) * 100).toFixed(2)}%`
const PLAN_TOP_K = 10

const loadingInventory = ref(false)
const loadingPlans = ref(false)
const loadingNextTier = ref(false)
const loadingCatalog = ref(false)
const loadingSession = ref(false)
const selectedPlanIndex = ref(0)
const sessionDialogVisible = ref(false)
const activePage = ref('overview')
const inventoryTask = ref(null)
const catalogTask = ref(null)
const taskLogs = ref([])
const taskLogKeys = new Set()
const taskPollers = new Map()

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
  catalogAction: '尚未同步目录数据',
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
      { label: '最高期望值', value: '--' },
      { label: '预计利润', value: '--' },
      { label: '预计回报率', value: '--' },
    ]
  }
  return [
    { label: '最高期望值', value: currency(plan.expectedOutputValue) },
    { label: '预计利润', value: currency(plan.expectedProfit) },
    { label: '预计回报率', value: percent(plan.roi) },
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
      kicker: '工作台',
      title: '工作总览',
      description: '查看会话、库存、目录数据和方案状态，并从这里进入下一步操作。',
    },
    inventory: {
      kicker: '库存',
      title: '炼金素材库存',
      description: '展示数据库中最近一次保存的武器库存，保留每一件独立饰品和完整磨损信息。',
    },
    plans: {
      kicker: '汰换方案',
      title: '期望值推荐方案',
      description: '按期望价值查看推荐合同，选中方案后核对输入素材和潜在产出。',
    },
    data: {
      kicker: '数据维护',
      title: '会话与数据维护',
      description: '维护 BUFF 会话、同步目录数据，并保存关联档位冗余数据。',
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
    label: '目录数据',
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

const isRunningTask = (task) => task?.status === 'PENDING' || task?.status === 'RUNNING'

const trackedTasks = computed(() => [inventoryTask.value, catalogTask.value].filter(Boolean))

const visibleTaskLogs = computed(() => taskLogs.value.slice(0, 24))

const taskProgressStatus = (task) => {
  if (task?.status === 'SUCCEEDED') {
    return 'success'
  }
  if (task?.status === 'FAILED') {
    return 'exception'
  }
  return undefined
}

const taskCounterText = (task) => {
  if (!task) {
    return ''
  }
  const current = task.current ?? null
  const total = task.total ?? null
  if (current === null && total === null) {
    return '等待后端返回处理进度'
  }
  if (total === null) {
    return `当前进度：${current}`
  }
  return `当前进度：${current || 0} / ${total}`
}

const taskTypeLabel = (type) => {
  const labels = {
    INVENTORY_FETCH: 'BUFF 库存',
    INVENTORY_FORCE_FETCH: '强制库存',
    CATALOG_SYNC: '目录同步',
  }
  return labels[type] || type || '后台任务'
}

const taskStatusLabel = (status) => {
  const labels = {
    PENDING: '等待中',
    RUNNING: '执行中',
    SUCCEEDED: '已完成',
    FAILED: '失败',
  }
  return labels[status] || status || '未知'
}

const formatTaskTime = (timestamp) => {
  const value = Number(timestamp || Date.now())
  return new Date(value).toLocaleTimeString('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

const recordTaskLog = (task, fallbackMessage = '') => {
  if (!task) {
    return
  }
  const message = task.errorMessage || task.message || fallbackMessage || '任务状态已更新'
  const key = [
    task.taskId || task.type || 'task',
    task.status || '',
    task.progress ?? '',
    task.current ?? '',
    task.total ?? '',
    message,
  ].join('|')
  if (taskLogKeys.has(key)) {
    return
  }
  taskLogKeys.add(key)
  taskLogs.value = [
    {
      id: `${Date.now()}-${taskLogs.value.length}`,
      taskId: task.taskId,
      type: task.type,
      status: task.status,
      progress: task.progress || 0,
      message,
      time: formatTaskTime(task.finishedAt || task.startedAt || task.createdAt || Date.now()),
    },
    ...taskLogs.value,
  ].slice(0, 40)
}

const updateInventoryTask = (task) => {
  inventoryTask.value = task
  recordTaskLog(task, '库存任务状态已更新')
}

const updateCatalogTask = (task) => {
  catalogTask.value = task
  recordTaskLog(task, '目录同步任务状态已更新')
}

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

const pollTask = (taskId, assignTask) => {
  if (taskPollers.has(taskId)) {
    clearTimeout(taskPollers.get(taskId))
  }

  return new Promise((resolve, reject) => {
    const poll = async () => {
      try {
        const task = await request(`/api/tasks/${taskId}`)
        assignTask(task)
        if (task.status === 'SUCCEEDED') {
          taskPollers.delete(taskId)
          resolve(task)
          return
        }
        if (task.status === 'FAILED') {
          taskPollers.delete(taskId)
          reject(new Error(task.errorMessage || task.message || '任务执行失败'))
          return
        }
        const timer = setTimeout(poll, 1500)
        taskPollers.set(taskId, timer)
      } catch (error) {
        taskPollers.delete(taskId)
        reject(error)
      }
    }

    poll()
  })
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
    const task = await postJson('/api/buff/inventory/fetch/task', {
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
    const task = await postJson('/api/buff/inventory/fetch/force/task', {
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

const syncCatalog = async () => {
  loadingCatalog.value = true
  try {
    const task = await postJson('/api/catalog/sync/task', {
      snapshotId: inventoryState.snapshotId,
    })
    updateCatalogTask(task)
    const finalTask = await pollTask(task.taskId, updateCatalogTask)
    const payload = finalTask.result || {}
    planState.catalogAction = payload.message || `已同步 ${payload.itemCount} 条目录数据`
    ElMessage.success(payload.message || `已同步 ${payload.itemCount} 条目录数据`)
  } catch (error) {
    const message = String(error.message || '同步目录数据失败')
    ElMessage.error(message || '同步目录数据失败')
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
      topK: PLAN_TOP_K,
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
      ElMessage.error('目录数据库为空，请先点击“从 BUFF 同步目录数据”')
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
      ElMessage.error('目录数据库为空，请先点击“从 BUFF 同步目录数据”')
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

onBeforeUnmount(() => {
  taskPollers.forEach((timer) => clearTimeout(timer))
  taskPollers.clear()
})
</script>

<template>
  <div class="workspace-shell">
    <aside class="workspace-sidebar">
      <div class="brand-lockup">
          <span>CT</span>
          <div>
            <strong>CS 汰换</strong>
          <small>汰换工作台</small>
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
                <span class="section-kicker">下一步</span>
                <h2>常用操作</h2>
              </div>
              <div class="action-list">
                <button type="button" class="action-row" @click="activePage = 'data'; sessionDialogVisible = true">
                  <strong>导入 BUFF 会话</strong>
                  <span>保存 Cookie 后，后端会托管后续抓取请求。</span>
                </button>
                <button type="button" class="action-row" @click="activePage = 'data'">
                  <strong>采集与同步数据</strong>
                  <span>统一处理 BUFF 库存抓取、强制刷新、目录同步和任务进度。</span>
                </button>
                <button type="button" class="action-row" @click="activePage = 'plans'; optimizePlans()">
                  <strong>生成推荐方案</strong>
                  <span>读取数据库库存和目录数据，按期望值排序。</span>
                </button>
              </div>
            </section>

            <section class="operation-panel">
              <div class="section-head">
                <span class="section-kicker">最佳方案</span>
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
              <span class="section-kicker">库存看板</span>
              <h2>库存看板</h2>
              <p class="surface-note">只展示数据库中最近一次保存的武器库存。需要重新抓取时，请到“数据”页启动后台任务。</p>
            </div>
            <div class="inline-actions">
              <el-button plain :loading="loadingInventory" @click="restorePersistedInventory">刷新看板</el-button>
              <el-button type="warning" @click="activePage = 'data'">去数据页采集</el-button>
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
              <span class="section-kicker">方案引擎</span>
              <h2>方案计算</h2>
              <p class="surface-note">方案计算读取数据库里最近一次保存的武器库存快照，并默认展示期望值前十的推荐方案。</p>
            </div>
            <el-button type="primary" :loading="loadingPlans" @click="optimizePlans">生成前十方案</el-button>
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
                <span class="section-kicker">BUFF 会话</span>
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
                <span class="section-kicker">数据任务</span>
                <h2>数据任务</h2>
              </div>
              <p class="surface-note">
                长任务统一在这里启动。库存抓取和目录同步可能持续几分钟，任务进度会在下方持续刷新。
              </p>
              <div class="data-job-grid">
                <button type="button" class="action-row" :disabled="loadingInventory" @click="fetchInventory">
                  <strong>{{ loadingInventory ? '库存抓取中' : '从 BUFF 获取库存' }}</strong>
                  <span>按页抓取 BUFF 库存，保存武器类素材快照。</span>
                </button>
                <button type="button" class="action-row" :disabled="loadingInventory" @click="forceFetchInventory">
                  <strong>{{ loadingInventory ? '强制刷新中' : '强制刷新库存' }}</strong>
                  <span>忽略远端变化判断，重新落库当前库存。</span>
                </button>
                <button type="button" class="action-row" :disabled="loadingCatalog" @click="syncCatalog">
                  <strong>{{ loadingCatalog ? '目录同步中' : '从 BUFF 同步目录数据' }}</strong>
                  <span>根据库存 goods_id 分批补全市场详情。</span>
                </button>
                <button type="button" class="action-row" :disabled="loadingNextTier" @click="persistNextTierCatalog">
                  <strong>{{ loadingNextTier ? '保存中' : '保存关联档位数据' }}</strong>
                  <span>为方案计算补齐上级/下级冗余数据。</span>
                </button>
              </div>
              <p class="surface-note">{{ planState.catalogAction }}</p>
              <p class="surface-note">{{ planState.nextTierAction }}</p>
            </section>

            <section class="operation-panel task-console-panel">
              <div class="section-head task-console-head">
                <div>
                  <span class="section-kicker">任务监控</span>
                  <h2>任务进度</h2>
                </div>
                <p class="surface-note">长任务会持续轮询后端状态，页面停留在这里也能看到当前页数、处理数量和限流提示。</p>
              </div>

              <div v-if="trackedTasks.length" class="task-overview-grid">
                <article
                  v-for="task in trackedTasks"
                  :key="task.taskId || task.type"
                  class="task-progress-panel task-monitor-card"
                  :class="String(task.status || '').toLowerCase()"
                >
                  <div class="task-progress-head">
                    <div>
                      <strong>{{ taskTypeLabel(task.type) }}</strong>
                      <span>{{ task.message || '等待后端返回处理进度' }}</span>
                    </div>
                    <em>{{ taskStatusLabel(task.status) }}</em>
                  </div>
                  <el-progress
                    :percentage="task.progress || 0"
                    :status="taskProgressStatus(task)"
                    :stroke-width="8"
                  />
                  <div class="task-monitor-meta">
                    <span>{{ taskCounterText(task) }}</span>
                    <span v-if="isRunningTask(task)">轮询中</span>
                    <span v-else>{{ taskStatusLabel(task.status) }}</span>
                  </div>
                  <p v-if="task.canContinue" class="surface-note">可稍后继续执行，已入库数据会自动跳过。</p>
                  <p v-if="task.errorMessage" class="surface-note danger-note">{{ task.errorMessage }}</p>
                </article>
              </div>
              <div v-else class="task-empty-state">
                <strong>暂无后台任务</strong>
                <span>点击“从 BUFF 获取”或“从 BUFF 同步目录数据”后，这里会显示实时进度。</span>
              </div>

              <div class="task-log-panel">
                <div class="task-log-title">
                  <strong>任务日志</strong>
                  <span>最近 {{ visibleTaskLogs.length }} 条</span>
                </div>
                <ol v-if="visibleTaskLogs.length" class="task-log-list">
                  <li v-for="log in visibleTaskLogs" :key="log.id" :class="String(log.status || '').toLowerCase()">
                    <time>{{ log.time }}</time>
                    <span>{{ taskTypeLabel(log.type) }}</span>
                    <strong>{{ taskStatusLabel(log.status) }}</strong>
                    <p>{{ log.message }}</p>
                  </li>
                </ol>
                <p v-else class="surface-note">还没有任务日志。启动长任务后，每次后端进度变化都会记录在这里。</p>
              </div>
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
