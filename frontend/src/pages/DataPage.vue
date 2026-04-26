<script setup>
defineProps({
  sessionState: {
    type: Object,
    required: true,
  },
  loadingSession: {
    type: Boolean,
    required: true,
  },
  loadingInventory: {
    type: Boolean,
    required: true,
  },
  loadingCatalog: {
    type: Boolean,
    required: true,
  },
  loadingNextTier: {
    type: Boolean,
    required: true,
  },
  planState: {
    type: Object,
    required: true,
  },
  canFetchInventory: {
    type: Boolean,
    required: true,
  },
  fetchInventoryDisabledReason: {
    type: String,
    default: '',
  },
  canSyncCatalog: {
    type: Boolean,
    required: true,
  },
  syncCatalogDisabledReason: {
    type: String,
    default: '',
  },
  canPersistNextTier: {
    type: Boolean,
    required: true,
  },
  persistNextTierDisabledReason: {
    type: String,
    default: '',
  },
  catalogMissing: {
    type: Boolean,
    required: true,
  },
  trackedTasks: {
    type: Array,
    required: true,
  },
  visibleTaskLogs: {
    type: Array,
    required: true,
  },
  taskCounterText: {
    type: Function,
    required: true,
  },
  taskProgressStatus: {
    type: Function,
    required: true,
  },
  taskStatusLabel: {
    type: Function,
    required: true,
  },
  taskTypeLabel: {
    type: Function,
    required: true,
  },
  isRunningTask: {
    type: Function,
    required: true,
  },
})

defineEmits([
  'open-session',
  'validate-session',
  'clear-session',
  'fetch-inventory',
  'force-fetch-inventory',
  'sync-catalog',
  'persist-next-tier',
])
</script>

<template>
  <section class="page-panel reveal-up">
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
            <el-button type="primary" :loading="loadingSession" @click="$emit('open-session')">导入会话</el-button>
            <el-button plain :loading="loadingSession" @click="$emit('validate-session')">校验会话</el-button>
            <el-button plain :loading="loadingSession" @click="$emit('clear-session')">清除会话</el-button>
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
        <div v-if="catalogMissing" class="action-hint-panel warning">
          <strong>目录数据为空</strong>
          <span>请先点击“从 BUFF 同步目录数据”，完成后再保存关联档位或生成方案。</span>
        </div>
        <div class="data-job-grid">
          <button type="button" class="action-row" :disabled="loadingInventory || !canFetchInventory" @click="$emit('fetch-inventory')">
            <strong>{{ canFetchInventory && loadingInventory ? '库存抓取中' : '从 BUFF 获取库存' }}</strong>
            <span>{{ fetchInventoryDisabledReason || '按页抓取 BUFF 库存，保存武器类素材快照。' }}</span>
          </button>
          <button type="button" class="action-row" :disabled="loadingInventory || !canFetchInventory" @click="$emit('force-fetch-inventory')">
            <strong>{{ canFetchInventory && loadingInventory ? '强制刷新中' : '强制刷新库存' }}</strong>
            <span>{{ fetchInventoryDisabledReason || '忽略远端变化判断，重新落库当前库存。' }}</span>
          </button>
          <button type="button" class="action-row" :disabled="loadingCatalog || !canSyncCatalog" @click="$emit('sync-catalog')">
            <strong>{{ loadingCatalog ? '目录同步中' : '从 BUFF 同步目录数据' }}</strong>
            <span>{{ syncCatalogDisabledReason || '根据库存 goods_id 分批补全市场详情。' }}</span>
          </button>
          <button type="button" class="action-row" :disabled="loadingNextTier || !canPersistNextTier" @click="$emit('persist-next-tier')">
            <strong>{{ loadingNextTier ? '保存中' : '保存关联档位数据' }}</strong>
            <span>{{ persistNextTierDisabledReason || '为方案计算补齐上级/下级冗余数据。' }}</span>
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
</template>
