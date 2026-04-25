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
})

defineEmits(['optimize-plans', 'select-plan'])
</script>

<template>
  <section class="page-panel reveal-up">
    <div class="page-toolbar">
      <div>
        <span class="section-kicker">方案引擎</span>
        <h2>方案计算</h2>
        <p class="surface-note">方案计算读取数据库里最近一次保存的武器库存快照，并默认展示期望值前十的推荐方案。</p>
      </div>
      <el-button type="primary" :loading="loadingPlans" @click="$emit('optimize-plans')">生成前十方案</el-button>
    </div>
    <p class="surface-note toolbar-note">{{ planState.lastAction }}</p>

    <PlanWorkspace
      :plans="sortedPlans"
      :selected-plan="selectedPlan"
      :selected-plan-index="selectedPlanIndex"
      @select-plan="$emit('select-plan', $event)"
    />
  </section>
</template>
