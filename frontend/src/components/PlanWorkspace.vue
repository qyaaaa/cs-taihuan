<script setup>
defineProps({
  plans: {
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

defineEmits(['select-plan'])

const currency = (value) => `¥${Number(value || 0).toFixed(2)}`
const percent = (value) => `${(Number(value || 0) * 100).toFixed(2)}%`
</script>

<template>
  <section class="plans-stage reveal-up">
    <div class="section-head">
      <span class="section-kicker">方案队列</span>
      <h2>期望值推荐方案</h2>
    </div>

    <div class="plans-layout">
      <aside class="plan-list">
        <button
          v-for="(plan, index) in plans"
          :key="index"
          type="button"
          class="plan-row"
          :class="{ active: selectedPlanIndex === index }"
          @click="$emit('select-plan', index)"
        >
          <div class="plan-row-top">
            <span>#{{ String(index + 1).padStart(2, '0') }}</span>
            <strong>{{ currency(plan.expectedOutputValue) }}</strong>
          </div>
          <h3>{{ plan.rarity }} 级合同</h3>
          <div class="plan-inline">
            <label>利润</label>
            <b>{{ currency(plan.expectedProfit) }}</b>
            <label>回报率</label>
            <b>{{ percent(plan.roi) }}</b>
          </div>
        </button>
      </aside>

      <section v-if="selectedPlan" class="plan-detail">
        <div class="detail-hero">
          <div>
            <span class="section-kicker">当前合同</span>
            <h3>{{ selectedPlan.rarity }} 级合同</h3>
          </div>
          <div class="detail-metrics">
            <div>
              <label>期望值</label>
              <strong>{{ currency(selectedPlan.expectedOutputValue) }}</strong>
            </div>
            <div>
              <label>投入</label>
              <strong>{{ currency(selectedPlan.inputCost) }}</strong>
            </div>
            <div>
              <label>利润</label>
              <strong>{{ currency(selectedPlan.expectedProfit) }}</strong>
            </div>
          </div>
        </div>

        <div class="detail-columns">
          <div>
            <h4>合同输入</h4>
            <div class="detail-list">
              <div v-for="(item, index) in selectedPlan.inputs" :key="index" class="detail-item">
                <div>
                  <strong>{{ item.name }}</strong>
                  <p>{{ item.collection || '未补全收藏品' }}</p>
                </div>
                <div class="detail-side">
                  <span>{{ currency(item.price) }}</span>
                  <em>{{ item.floatValue == null ? '--' : item.floatValue.toFixed(4) }}</em>
                </div>
              </div>
            </div>
          </div>

          <div>
            <h4>潜在产出</h4>
            <div class="detail-list">
              <div v-for="(outcome, index) in selectedPlan.outcomes" :key="index" class="detail-item">
                <div>
                  <strong>{{ outcome.skin.name }}</strong>
                  <p>{{ outcome.skin.collection }}</p>
                </div>
                <div class="detail-side">
                  <span>{{ percent(outcome.probability) }}</span>
                  <em>{{ currency(outcome.estimatedSalePrice) }}</em>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section v-else class="plan-detail empty-state">
        <h3>还没有方案</h3>
        <p>先抓取库存或载入本地库存，再运行一次方案计算。</p>
      </section>
    </div>
  </section>
</template>
