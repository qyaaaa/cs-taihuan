<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAccounts } from './composables/useAccounts'
import { useInventory } from './composables/useInventory'
import { usePlans } from './composables/usePlans'
import { useSession } from './composables/useSession'
import { useTaskMonitor } from './composables/useTaskMonitor'
import CollectionBrowserPage from './pages/CollectionBrowserPage.vue'
import DataPage from './pages/DataPage.vue'
import FloatCalculatorPage from './pages/FloatCalculatorPage.vue'
import InventoryPage from './pages/InventoryPage.vue'
import OddsGalleryPage from './pages/OddsGalleryPage.vue'
import OverviewPage from './pages/OverviewPage.vue'
import PlansPage from './pages/PlansPage.vue'
import { currency, percent } from './utils/formatters'

// 当前 tab 由 URL 决定：刷新 / 前进后退 / 深链都会停留在对应页面。无效/已移除的 tab 回退到总览。
const VALID_PAGES = ['overview', 'inventory', 'plans', 'float', 'odds', 'collections']
const route = useRoute()
const router = useRouter()
const activePage = computed(() => (VALID_PAGES.includes(route.name) ? route.name : 'overview'))
const accountDialogVisible = ref(false)
// When true, the session dialog was opened via 「新增」 and no account exists yet;
// the account is created only after a successful scan / manual cookie save.
const pendingNewAccount = ref(false)

const taskMonitor = useTaskMonitor()
const accounts = useAccounts()
const currentAccountId = computed(() => accounts.accountState.currentAccountId)
const currentAccount = accounts.currentAccount

const inventory = useInventory({
  pollTask: taskMonitor.pollTask,
  updateInventoryTask: taskMonitor.updateInventoryTask,
  accountId: currentAccountId,
})

const plans = usePlans({
  inventoryState: inventory.inventoryState,
  pollTask: taskMonitor.pollTask,
  updateCatalogTask: taskMonitor.updateCatalogTask,
  accountId: currentAccountId,
})

const session = useSession({
  restorePersistedInventory: inventory.restorePersistedInventory,
  accountId: currentAccountId,
  onAccountUpdated: accounts.loadAccounts,
  pendingNewAccount,
  createLocalAccount: accounts.createLocalAccount,
  selectAccount: accounts.changeAccount,
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
    float: {
      kicker: '磨损计算',
      title: '特殊磨损计算器',
      description: '反推目标产物磨损所需的下级平均磨损，并在锁定部分材料后重算剩余需求。',
    },
    odds: {
      kicker: '概率图鉴',
      title: '容器概率图鉴',
      description: '按武器箱、纪念包、收藏包、胶囊、布章包和武库补充规则展示中奖概率。',
    },
    collections: {
      kicker: '收藏品图鉴',
      title: '收藏品与磨损范围',
      description: '展示当前基准库中的收藏品、全部产物，以及每个皮肤自身的磨损上下限。',
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
    label: '汰换方案',
    metric: `${plans.sortedPlans.value.length} 条`,
  },
  {
    key: 'float',
    label: '特殊磨损计算器',
    metric: '反推',
  },
  {
    key: 'odds',
    label: '概率图鉴',
    metric: '规则',
  },
  {
    key: 'collections',
    label: '收藏品',
    metric: '范围',
  },
])

const statusCards = computed(() => [
  {
    label: 'BUFF 会话',
    value: session.sessionState.connected
      ? (session.sessionState.valid ? '有效' : (session.loadingSession.value ? '校验中' : '无效'))
      : '未登录',
    note: session.sessionState.maskedCookie || '尚未导入 Cookie',
    target: 'overview',
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
    target: 'overview',
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
  if (session.sessionState.connected) {
    return session.loadingSession.value ? '正在自动校验 BUFF 会话。' : 'BUFF 会话未通过校验，请重新导入会话。'
  }
  return '请先导入 BUFF 会话，保存后会自动校验。'
})

const snapshotRequiredReason = computed(() => {
  return hasInventorySnapshot.value ? '' : '请先从 BUFF 获取库存，生成数据库库存快照。'
})

const catalogMissingReason = computed(() => {
  return plans.catalogMissing.value ? '目录数据库为空，请先在总览页同步目录数据。' : ''
})

const planDisabledReason = computed(() => {
  return snapshotRequiredReason.value || catalogMissingReason.value
})

const pageLoading = computed(() => {
  if (activePage.value === 'inventory') {
    return inventory.loadingInventoryPage.value
  }
  if (activePage.value === 'plans') {
    return plans.loadingPlans.value
  }
  return session.loadingSession.value || inventory.loadingInventoryPage.value
})

const pageLoadingText = computed(() => {
  if (activePage.value === 'plans') {
    return '正在生成推荐方案'
  }
  if (activePage.value === 'inventory') {
    return '正在加载库存数据'
  }
  if (inventory.loadingInventoryPage.value) {
    return '正在加载库存数据'
  }
  if (session.loadingSession.value) {
    return '正在校验 BUFF 会话'
  }
  return '加载中'
})

const generatePlansOnEnter = () => {
  if (planDisabledReason.value || plans.loadingPlans.value) {
    return
  }
  plans.optimizePlans()
}

const changePage = (page, options = {}) => {
  const fromPage = activePage.value
  router.push({ name: page }).catch(() => {})
  if (page === 'plans' && (fromPage !== 'plans' || options.forceGenerate)) {
    generatePlansOnEnter()
  }
}

const openSessionDialog = () => {
  // Importing a session for the currently selected account (not a new account).
  pendingNewAccount.value = false
  changePage('overview')
  session.sessionDialogVisible.value = true
}

const openBuffLogin = () => {
  window.open('https://buff.163.com/market/csgo', '_blank', 'noopener,noreferrer')
}

const refreshCurrentAccountData = async () => {
  inventory.resetInventory()
  plans.resetPlans()
  taskMonitor.resetTasks()
  await inventory.restorePersistedInventory().catch(() => {})
  await session.loadSessionStatus()
}

const handleAccountChange = async (accountId) => {
  const account = accounts.changeAccount(accountId)
  if (!account) {
    return
  }
  await refreshCurrentAccountData()
}

const onSessionDialogClosed = () => {
  session.resetQrLogin()
  pendingNewAccount.value = false
}

const openNewAccountDialog = () => {
  // Don't create the account yet — only open the login dialog. The account is created
  // once a scan succeeds or a cookie is saved.
  pendingNewAccount.value = true
  session.sessionDialogVisible.value = true
}

const saveAccount = async (account) => {
  const updated = await accounts.updateLocalAccount(account.id, {
    nickname: account.nickname,
    buffUserId: account.buffUserId || null,
  })
  if (updated) {
    await refreshCurrentAccountData()
  }
}

const deleteManagedAccount = async (account) => {
  try {
    await ElMessageBox.confirm(
      `删除本地账号「${account.nickname}」后，这个账号的会话记录会一起清除。`,
      '删除账号',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
  } catch (error) {
    return
  }
  const wasCurrent = account.id === accounts.accountState.currentAccountId
  const deleted = await accounts.deleteLocalAccount(account.id)
  if (deleted && wasCurrent) {
    await refreshCurrentAccountData()
  }
}

const optimizeFromOverview = () => {
  if (planDisabledReason.value) {
    changePage('overview')
    return
  }
  changePage('plans', { forceGenerate: true })
}

onMounted(async () => {
  await accounts.loadAccounts()
  await refreshCurrentAccountData()
  // 直接刷新/深链进入方案页时也自动生成方案（changePage 只在切 tab 时触发，刷新不走它）。
  if (activePage.value === 'plans') {
    generatePlansOnEnter()
  }
})
</script>

<template>
  <div class="workspace-shell">
    <aside class="workspace-sidebar">
      <div class="brand-lockup">
        <img src="/app-icon.svg" alt="CSGO 汰换爆赚" />
        <div>
          <strong>CS 汰换</strong>
          <small>汰换工作台</small>
        </div>
      </div>

      <div class="account-switcher">
        <label>当前账号</label>
        <div class="account-switcher-row">
          <el-select
            :model-value="accounts.accountState.currentAccountId"
            placeholder="选择账号"
            :loading="accounts.accountState.loading"
            size="small"
            @change="handleAccountChange"
          >
            <el-option
              v-for="account in accounts.accountState.accounts"
              :key="account.id"
              :label="account.nickname"
              :value="account.id"
            >
              <span>{{ account.nickname }}</span>
              <small>{{ account.status === 'VALID' ? '已登录' : account.status === 'INVALID' ? '失效' : '未校验' }}</small>
            </el-option>
          </el-select>
          <el-button size="small" plain :loading="accounts.accountState.loading" @click="openNewAccountDialog">新增</el-button>
          <el-button size="small" plain @click="accountDialogVisible = true">管理</el-button>
        </div>
      </div>

      <nav class="workspace-nav" aria-label="主导航">
        <button
          v-for="item in navItems"
          :key="item.key"
          type="button"
          class="nav-item"
          :class="{ active: activePage === item.key }"
          @click="changePage(item.key)"
        >
          <span>{{ item.label }}</span>
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
        <div class="session-compact" :class="{ active: session.sessionState.connected, offline: currentAccount?.status === 'INVALID' }">
          <span>{{ currentAccount?.status === 'INVALID' ? 'BUFF 已掉线' : (session.sessionState.connected ? 'BUFF 已托管' : 'BUFF 未登录') }}</span>
          <strong>{{ currentAccount?.nickname || '默认账号' }}</strong>
          <el-button
            v-if="currentAccount?.status === 'INVALID'"
            size="small"
            type="warning"
            class="session-relogin-btn"
            @click="openSessionDialog"
          >重新扫码登录</el-button>
        </div>
      </header>

      <main class="page-stack" :class="{ 'is-loading': pageLoading }" :aria-busy="pageLoading">
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
          :loading-inventory="inventory.loadingInventoryPage.value"
          :inventory-state="inventory.inventoryState"
          :inventory-stats="inventory.inventoryStats.value"
          :inventory-items="inventory.inventoryItems.value"
          :rarity-options="inventory.inventoryRarityOptions"
          @restore-inventory="inventory.restorePersistedInventory"
          @go-data="changePage('overview')"
          @page-change="inventory.changeInventoryPage"
          @update-rarity="inventory.changeInventoryRarity"
        />

        <PlansPage
          v-else-if="activePage === 'plans'"
          :loading-plans="plans.loadingPlans.value"
          :plan-state="plans.planState"
          :sorted-plans="plans.sortedPlans.value"
          :selected-plan="plans.selectedPlan.value"
          :selected-plan-index="plans.selectedPlanIndex.value"
          :plan-filters="plans.planFilters"
          :rarity-options="plans.rarityOptions.value"
          :can-generate-plans="!planDisabledReason"
          :generate-disabled-reason="planDisabledReason"
          :catalog-missing="plans.catalogMissing.value"
          :loading-backfill="plans.loadingBackfill.value"
          @optimize-plans="plans.optimizePlans"
          @go-data="changePage('overview')"
          @select-plan="plans.selectedPlanIndex.value = $event"
          @update-filter="plans.updatePlanFilter"
          @backfill-collection="plans.backfillOutcomes"
        />

        <FloatCalculatorPage
          v-else-if="activePage === 'float'"
          :account-id="currentAccountId"
          :snapshot-id="inventory.inventoryState.snapshotId"
        />

        <OddsGalleryPage v-else-if="activePage === 'odds'" />

        <CollectionBrowserPage v-else-if="activePage === 'collections'" />

        <DataPage
          v-if="activePage === 'overview'"
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
          @open-session="openSessionDialog"
          @clear-session="session.clearSession"
          @fetch-inventory="inventory.fetchInventory"
          @force-fetch-inventory="inventory.forceFetchInventory"
          @sync-catalog="plans.syncCatalog"
          @persist-next-tier="plans.persistNextTierCatalog"
        />

        <div v-if="pageLoading" class="page-loading-cover" role="status" aria-live="polite">
          <span class="loading-ring" aria-hidden="true"></span>
          <strong>{{ pageLoadingText }}</strong>
        </div>
      </main>
    </div>

    <el-dialog
      v-model="session.sessionDialogVisible.value"
      title="登录 BUFF"
      width="640px"
      class="session-dialog"
      @closed="onSessionDialogClosed"
    >
      <el-tabs v-model="session.qrLogin.activeTab" class="session-tabs">
        <el-tab-pane label="扫码登录" name="qrcode">
          <div class="qrcode-login-panel">
            <p class="dialog-copy">
              后端自动打开 BUFF 登录页，获取登录二维码。使用网易 BUFF App 扫码即可完成登录，会话由后端托管。
            </p>

            <!-- QR code image -->
            <div v-if="session.qrLogin.qrcode" class="qrcode-display">
              <img
                :src="'data:image/png;base64,' + session.qrLogin.qrcode"
                alt="BUFF 登录二维码"
                class="qrcode-image"
              />
            </div>
            <div v-else-if="session.qrLogin.status === 'IDLE'" class="qrcode-placeholder">
              <span class="qrcode-placeholder-icon">📱</span>
              <span>点击下方按钮获取登录二维码</span>
            </div>

            <!-- Status message -->
            <div v-if="session.qrLogin.status !== 'IDLE'" class="qrcode-status" :class="session.qrLogin.status.toLowerCase()">
              <span v-if="session.qrLogin.status === 'PENDING'">⏳ 等待扫码中...</span>
              <span v-else-if="session.qrLogin.status === 'SCANNED'">📱 已扫码，请在网易 BUFF App 中确认</span>
              <span v-else-if="session.qrLogin.status === 'CONFIRMED'">✅ 已确认，正在完成登录...</span>
              <span v-else-if="session.qrLogin.status === 'EXPIRED'">⏰ 二维码已过期，请重新获取</span>
              <span v-else-if="session.qrLogin.status === 'FAILED'">❌ 登录失败，请重试</span>
              <span v-else>{{ session.qrLogin.message }}</span>
            </div>

            <div class="qrcode-actions">
              <el-button
                type="primary"
                :loading="session.loadingSession.value"
                :disabled="session.loadingSession.value || session.qrLogin.status === 'PENDING' || session.qrLogin.status === 'SCANNED' || session.qrLogin.status === 'CONFIRMED'"
                @click="session.beginQrLogin"
              >
                {{ session.qrLogin.status === 'EXPIRED' || session.qrLogin.status === 'FAILED' ? '重新获取二维码' : '获取登录二维码' }}
              </el-button>
              <el-button
                v-if="session.qrLogin.active"
                plain
                @click="session.cancelCurrentQrLogin"
              >
                取消
              </el-button>
            </div>

            <p class="surface-note subtle-note qrcode-hint">
              需要后端服务器能够访问 BUFF 登录页。如服务器网络受限，请使用「手动导入」方式。
            </p>
          </div>
        </el-tab-pane>

        <el-tab-pane label="手动导入" name="manual">
          <div class="session-import-head">
            <div>
              <p class="dialog-copy">
                先打开 BUFF 并确认浏览器已登录，再复制请求头里的完整 Cookie。保存后由后端托管，库存抓取会自动复用它。
              </p>
              <p class="surface-note">
                本地页面不能跨域读取 BUFF Cookie，所以这里不会自动获取浏览器会话。
              </p>
            </div>
            <el-button type="primary" plain @click="openBuffLogin">打开 BUFF</el-button>
          </div>
          <div class="session-import-steps">
            <div class="session-step">
              <strong>1</strong>
              <span>在新标签页登录 BUFF，并打开任意市场或库存页面。</span>
            </div>
            <div class="session-step">
              <strong>2</strong>
              <span>打开浏览器开发者工具，找到 BUFF 请求里的 `Cookie` 请求头。</span>
            </div>
            <div class="session-step">
              <strong>3</strong>
              <span>复制完整 Cookie 粘贴到下方，保存后系统会自动校验。</span>
            </div>
          </div>
          <el-input
            v-model="session.sessionForm.cookie"
            type="textarea"
            :rows="8"
            placeholder="session=...; csrf_token=...; Device-Id=..."
          />
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <div class="dialog-actions">
          <el-button @click="session.sessionDialogVisible.value = false">取消</el-button>
          <el-button
            v-if="session.qrLogin.activeTab === 'manual'"
            type="primary"
            :loading="session.loadingSession.value"
            @click="session.saveSession"
          >
            保存会话
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="accountDialogVisible"
      title="账号管理"
      width="720px"
      class="account-dialog"
    >
      <div class="account-manager">
        <div
          v-for="account in accounts.accountState.accounts"
          :key="account.id"
          class="account-manager-row"
        >
          <div class="account-manager-main">
            <el-input v-model="account.nickname" maxlength="64" placeholder="账号昵称" />
            <el-input v-model="account.buffUserId" maxlength="128" placeholder="BUFF 用户 ID（可选）" />
          </div>
          <div class="account-manager-meta">
            <span>{{ account.status === 'VALID' ? '已登录' : account.status === 'INVALID' ? 'Cookie 失效' : '未校验' }}</span>
            <small>{{ account.maskedCookie || '未导入 Cookie' }}</small>
          </div>
          <div class="account-manager-actions">
            <el-button size="small" type="primary" plain :loading="accounts.accountState.loading" @click="saveAccount(account)">保存</el-button>
            <el-button size="small" plain :disabled="accounts.accountState.accounts.length <= 1" @click="deleteManagedAccount(account)">删除</el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="dialog-actions">
          <el-button @click="accountDialogVisible = false">关闭</el-button>
          <el-button type="primary" plain :loading="accounts.accountState.loading" @click="openNewAccountDialog">新增账号</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
