<script setup>
import { computed, onMounted, ref } from 'vue'
import { useInventory } from './composables/useInventory'
import { usePlans } from './composables/usePlans'
import { useSession } from './composables/useSession'
import { useTaskMonitor } from './composables/useTaskMonitor'
import DataPage from './pages/DataPage.vue'
import InventoryPage from './pages/InventoryPage.vue'
import OverviewPage from './pages/OverviewPage.vue'
import PlansPage from './pages/PlansPage.vue'
import { currency, percent } from './utils/formatters'

const activePage = ref('overview')

const taskMonitor = useTaskMonitor()

const inventory = useInventory({
  pollTask: taskMonitor.pollTask,
  updateInventoryTask: taskMonitor.updateInventoryTask,
})

const plans = usePlans({
  inventoryState: inventory.inventoryState,
  pollTask: taskMonitor.pollTask,
  updateCatalogTask: taskMonitor.updateCatalogTask,
})

const session = useSession({
  restorePersistedInventory: inventory.restorePersistedInventory,
})

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

const navItems = computed(() => [
  {
    key: 'overview',
    label: '总览',
    metric: inventory.inventoryState.snapshotId ? `#${inventory.inventoryState.snapshotId}` : '未载入',
  },
  {
    key: 'inventory',
    label: '库存',
    metric: `${inventory.inventoryStats.value[0]?.value || '00'} 件`,
  },
  {
    key: 'plans',
    label: '方案',
    metric: `${plans.sortedPlans.value.length} 条`,
  },
  {
    key: 'data',
    label: '数据',
    metric: session.sessionState.connected ? '已连接' : '未登录',
  },
])

const statusCards = computed(() => [
  {
    label: 'BUFF 会话',
    value: session.sessionState.connected ? (session.sessionState.valid ? '有效' : '待校验') : '未登录',
    note: session.sessionState.maskedCookie || '尚未导入 Cookie',
    target: 'data',
  },
  {
    label: '库存快照',
    value: inventory.inventoryState.snapshotId ? `#${inventory.inventoryState.snapshotId}` : '--',
    note: inventory.inventoryState.lastAction,
    target: 'inventory',
  },
  {
    label: '目录数据',
    value: plans.loadingCatalog.value ? '同步中' : '就绪',
    note: plans.planState.catalogAction,
    target: 'data',
  },
  {
    label: '推荐方案',
    value: `${plans.sortedPlans.value.length} 条`,
    note: plans.planState.lastAction,
    target: 'plans',
  },
])

const summaryRibbon = computed(() => {
  const plan = plans.selectedPlan.value
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

const hasValidSession = computed(() => Boolean(session.sessionState.connected && session.sessionState.valid))
const hasInventorySnapshot = computed(() => Boolean(inventory.inventoryState.snapshotId))

const fetchInventoryDisabledReason = computed(() => {
  if (hasValidSession.value) {
    return ''
  }
  return session.sessionState.connected ? '请先校验 BUFF 会话，确认登录仍然有效。' : '请先导入并校验 BUFF 会话。'
})

const snapshotRequiredReason = computed(() => {
  return hasInventorySnapshot.value ? '' : '请先从 BUFF 获取库存，生成数据库库存快照。'
})

const catalogMissingReason = computed(() => {
  return plans.catalogMissing.value ? '目录数据库为空，请先到数据页同步目录数据。' : ''
})

const planDisabledReason = computed(() => {
  return snapshotRequiredReason.value || catalogMissingReason.value
})

const changePage = (page) => {
  activePage.value = page
}

const openSessionDialog = () => {
  activePage.value = 'data'
  session.sessionDialogVisible.value = true
}

const optimizeFromOverview = () => {
  if (planDisabledReason.value) {
    activePage.value = planDisabledReason.value === snapshotRequiredReason.value ? 'data' : 'data'
    return
  }
  activePage.value = 'plans'
  plans.optimizePlans()
}

onMounted(() => {
  inventory.restorePersistedInventory().catch(() => {})
  session.loadSessionStatus()
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
        <div class="session-compact" :class="{ active: session.sessionState.connected }">
          <span>{{ session.sessionState.connected ? 'BUFF 已托管' : 'BUFF 未登录' }}</span>
          <strong>{{ session.sessionState.maskedCookie || 'No session' }}</strong>
        </div>
      </header>

      <main class="page-stack">
        <OverviewPage
          v-if="activePage === 'overview'"
          :status-cards="statusCards"
          :summary-ribbon="summaryRibbon"
          :can-optimize-plans="!planDisabledReason"
          :optimize-disabled-reason="planDisabledReason"
          @change-page="changePage"
          @open-session="openSessionDialog"
          @optimize-plans="optimizeFromOverview"
        />

        <InventoryPage
          v-else-if="activePage === 'inventory'"
          :loading-inventory="inventory.loadingInventory.value"
          :inventory-state="inventory.inventoryState"
          :inventory-stats="inventory.inventoryStats.value"
          :inventory-items="inventory.inventoryItems.value"
          @restore-inventory="inventory.restorePersistedInventory"
          @go-data="activePage = 'data'"
          @page-change="inventory.changeInventoryPage"
        />

        <PlansPage
          v-else-if="activePage === 'plans'"
          :loading-plans="plans.loadingPlans.value"
          :plan-state="plans.planState"
          :sorted-plans="plans.sortedPlans.value"
          :selected-plan="plans.selectedPlan.value"
          :selected-plan-index="plans.selectedPlanIndex.value"
          :can-generate-plans="!planDisabledReason"
          :generate-disabled-reason="planDisabledReason"
          :catalog-missing="plans.catalogMissing.value"
          @optimize-plans="plans.optimizePlans"
          @go-data="activePage = 'data'"
          @select-plan="plans.selectedPlanIndex.value = $event"
        />

        <DataPage
          v-else-if="activePage === 'data'"
          :session-state="session.sessionState"
          :loading-session="session.loadingSession.value"
          :loading-inventory="inventory.loadingInventory.value"
          :loading-catalog="plans.loadingCatalog.value"
          :loading-next-tier="plans.loadingNextTier.value"
          :plan-state="plans.planState"
          :can-fetch-inventory="!fetchInventoryDisabledReason"
          :fetch-inventory-disabled-reason="fetchInventoryDisabledReason"
          :can-sync-catalog="!snapshotRequiredReason"
          :sync-catalog-disabled-reason="snapshotRequiredReason"
          :can-persist-next-tier="!snapshotRequiredReason && !plans.catalogMissing.value"
          :persist-next-tier-disabled-reason="snapshotRequiredReason || catalogMissingReason"
          :catalog-missing="plans.catalogMissing.value"
          :tracked-tasks="taskMonitor.trackedTasks.value"
          :visible-task-logs="taskMonitor.visibleTaskLogs.value"
          :task-counter-text="taskMonitor.taskCounterText"
          :task-progress-status="taskMonitor.taskProgressStatus"
          :task-status-label="taskMonitor.taskStatusLabel"
          :task-type-label="taskMonitor.taskTypeLabel"
          :is-running-task="taskMonitor.isRunningTask"
          @open-session="session.sessionDialogVisible.value = true"
          @validate-session="session.validateSession"
          @clear-session="session.clearSession"
          @fetch-inventory="inventory.fetchInventory"
          @force-fetch-inventory="inventory.forceFetchInventory"
          @sync-catalog="plans.syncCatalog"
          @persist-next-tier="plans.persistNextTierCatalog"
        />
      </main>
    </div>

    <el-dialog
      v-model="session.sessionDialogVisible.value"
      title="导入 BUFF 会话"
      width="560px"
      class="session-dialog"
    >
      <p class="dialog-copy">
        先在浏览器登录 BUFF，然后把请求头里的完整 Cookie 粘贴到这里。保存后由后端托管，库存抓取会自动复用它。
      </p>
      <el-input
        v-model="session.sessionForm.cookie"
        type="textarea"
        :rows="8"
        placeholder="session=...; csrf_token=...; Device-Id=..."
      />
      <template #footer>
        <div class="dialog-actions">
          <el-button @click="session.sessionDialogVisible.value = false">取消</el-button>
          <el-button type="primary" :loading="session.loadingSession.value" @click="session.saveSession">保存会话</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
