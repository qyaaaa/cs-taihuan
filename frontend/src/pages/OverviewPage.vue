<script setup>
defineProps({
  statusCards: {
    type: Array,
    required: true,
  },
  summaryRibbon: {
    type: Array,
    required: true,
  },
})

defineEmits(['change-page', 'open-session', 'optimize-plans'])
</script>

<template>
  <section class="page-panel reveal-up">
    <div class="overview-grid">
      <button
        v-for="card in statusCards"
        :key="card.label"
        type="button"
        class="status-card"
        @click="$emit('change-page', card.target)"
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
          <button type="button" class="action-row" @click="$emit('open-session')">
            <strong>导入 BUFF 会话</strong>
            <span>保存 Cookie 后，后端会托管后续抓取请求。</span>
          </button>
          <button type="button" class="action-row" @click="$emit('change-page', 'data')">
            <strong>采集与同步数据</strong>
            <span>统一处理 BUFF 库存抓取、强制刷新、目录同步和任务进度。</span>
          </button>
          <button type="button" class="action-row" @click="$emit('optimize-plans')">
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
</template>
