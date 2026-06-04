<script setup>
import PlanWorkspace from '../components/PlanWorkspace.vue'

defineProps({
  loadingPlans: {
    type: Boolean,
    required: true,
  },
  planState: {
    type: Object,
    required: true,
  },
  sortedPlans: {
    type: Array,
    required: true,
  },
  selectedPlan: {
    type: Object,
    default: null,
  },
  selectedPlanIndex: {
    type: Number,
    required: true,
  },
  planFilters: {
    type: Object,
    required: true,
  },
  rarityOptions: {
    type: Array,
    required: true,
  },
  canGeneratePlans: {
    type: Boolean,
    required: true,
  },
  generateDisabledReason: {
    type: String,
    default: '',
  },
  catalogMissing: {
    type: Boolean,
    required: true,
  },
})

defineEmits(['optimize-plans', 'select-plan', 'go-data', 'update-filter'])
</script>

<template>
  <section class="page-panel reveal-up">
    <div class="page-toolbar">
      <div>
        <span class="section-kicker">方案引擎</span>
        <h2>方案计算</h2>
        <p class="surface-note">方案计算读取数据库里最近一次保存的武器库存快照，并默认展示期望值前十的推荐方案。</p>
      </div>
      <el-button type="primary" :loading="loadingPlans" :disabled="!canGeneratePlans" @click="$emit('optimize-plans')">生成前十方案</el-button>
    </div>
    <p class="surface-note toolbar-note">{{ planState.lastAction }}</p>
    <div v-if="generateDisabledReason" class="action-hint-panel warning">
      <strong>{{ catalogMissing ? '目录数据为空' : '暂不能生成方案' }}</strong>
      <span>{{ generateDisabledReason }}</span>
      <button type="button" class="inline-link-button" @click="$emit('go-data')">
        {{ catalogMissing ? '去同步目录数据' : '去总览页准备数据' }}
      </button>
    </div>

    <PlanWorkspace
      :plans="sortedPlans"
      :selected-plan="selectedPlan"
      :selected-plan-index="selectedPlanIndex"
      :total-plan-count="planState.plans.length"
      :plan-filters="planFilters"
      :rarity-options="rarityOptions"
      @select-plan="$emit('select-plan', $event)"
      @update-filter="(key, value) => $emit('update-filter', key, value)"
    />
  </section>
</template>
