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
  totalPlanCount: {
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
})

defineEmits(['select-plan', 'update-filter'])

const rarityOrder = ['consumer', 'industrial', 'mil-spec', 'restricted', 'classified', 'covert', 'gold']
const rarityLabels = {
  consumer: '消费级',
  industrial: '工业级',
  'mil-spec': '军规级',
  restricted: '受限级',
  classified: '保密级',
  covert: '隐秘级',
  gold: '金色',
}

const sortOptions = [
  { value: 'expectedOutputValue', label: '期望值高到低' },
  { value: 'expectedProfit', label: '利润高到低' },
  { value: 'roi', label: 'ROI 高到低' },
  { value: 'inputCost', label: '投入成本低到高' },
  { value: 'rarityRank', label: '档位高到低' },
]

const trackOptions = [
  { value: 'all', label: '全部' },
  { value: 'normal', label: '普通' },
  { value: 'stattrak', label: 'StatTrak' },
]

const contractOptions = [
  { value: 'all', label: '全部' },
  { value: 'regular', label: '常规合成' },
  { value: 'gold', label: 'Gold 合成' },
]

const currency = (value) => `¥${Number(value || 0).toFixed(2)}`
const percent = (value) => `${(Number(value || 0) * 100).toFixed(2)}%`
const rarityLabel = (rarity) => rarityLabels[rarity] || rarity || '未知档位'
const nextRarity = (rarity) => rarityOrder[rarityOrder.indexOf(rarity) + 1]
const imageSource = (item) => {
  return (
    item?.imageUrl
    || item?.raw?.original_icon_url
    || item?.raw?.icon_url
    || item?.raw?.asset_info?.info?.original_icon_url
    || item?.raw?.asset_info?.info?.icon_url
    || ''
  )
}
const isStatTrakPlan = (plan) => (plan?.inputs || []).some((item) => /stattrak/i.test(item?.name || ''))
const isGoldContract = (plan) => plan?.rarity === 'covert'
const contractTitle = (rarity) => {
  const target = nextRarity(rarity)
  return target ? `${rarityLabel(rarity)} -> ${rarityLabel(target)}` : `${rarityLabel(rarity)} 合同`
}
</script>

<template>
  <section class="plans-stage reveal-up">
    <div class="section-head">
      <span class="section-kicker">方案队列</span>
      <h2>期望值推荐方案</h2>
    </div>

    <div class="plan-controls">
      <div class="control-field">
        <label>排序</label>
        <el-select
          :model-value="planFilters.sortBy"
          size="small"
          @change="$emit('update-filter', 'sortBy', $event)"
        >
          <el-option v-for="option in sortOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </div>
      <div class="control-field">
        <label>档位</label>
        <el-select
          :model-value="planFilters.rarity"
          size="small"
          @change="$emit('update-filter', 'rarity', $event)"
        >
          <el-option label="全部" value="all" />
          <el-option
            v-for="rarity in rarityOptions"
            :key="rarity"
            :label="rarityLabel(rarity)"
            :value="rarity"
          />
        </el-select>
      </div>
      <div class="control-field">
        <label>类型</label>
        <el-segmented
          :model-value="planFilters.trackType"
          :options="trackOptions"
          size="small"
          @change="$emit('update-filter', 'trackType', $event)"
        />
      </div>
      <div class="control-field">
        <label>合成</label>
        <el-segmented
          :model-value="planFilters.contractType"
          :options="contractOptions"
          size="small"
          @change="$emit('update-filter', 'contractType', $event)"
        />
      </div>
      <div class="plan-count">
        {{ plans.length }} / {{ totalPlanCount }}
      </div>
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
          <h3>{{ contractTitle(plan.rarity) }}</h3>
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
            <h3>{{ contractTitle(selectedPlan.rarity) }}</h3>
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
            <div>
              <label>ROI</label>
              <strong>{{ percent(selectedPlan.roi) }}</strong>
            </div>
          </div>
        </div>

        <div v-if="isGoldContract(selectedPlan) && isStatTrakPlan(selectedPlan)" class="plan-insight">
          <strong>StatTrak 金色合成</strong>
          <span>该方案只使用可通向暗金刀的隐秘输入；暗金手套下级无法参与汰换，因此不会和暗金刀下级混入同一份合同。</span>
        </div>

        <div class="detail-columns">
          <div>
            <h4>合同输入</h4>
            <div class="detail-list">
              <div v-for="(item, index) in selectedPlan.inputs" :key="index" class="detail-item with-thumb">
                <img v-if="imageSource(item)" :src="imageSource(item)" :alt="item.name" class="detail-thumb" />
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
        <h3>{{ totalPlanCount > 0 ? '没有符合条件的方案' : '还没有方案' }}</h3>
        <p>{{ totalPlanCount > 0 ? '调整筛选条件后再查看。' : '先抓取库存或载入本地库存，再运行一次方案计算。' }}</p>
      </section>
    </div>
  </section>
</template>
