<script setup>
import { computed, ref } from 'vue'

const QUALITY_META = {
  消费级: { english: 'Consumer', color: '#8f9aaa' },
  工业级: { english: 'Industrial', color: '#5aa3ff' },
  军规级: { english: 'Mil-Spec', color: '#4d6bff' },
  受限: { english: 'Restricted', color: '#8f6bff' },
  保密: { english: 'Classified', color: '#d85cff' },
  隐秘: { english: 'Covert', color: '#ef5b69' },
  '罕见特殊物品': { english: 'Rare Special', color: '#f6c453' },
  高级: { english: 'High Grade', color: '#5aa3ff' },
  卓越: { english: 'Remarkable', color: '#8f6bff' },
  奇异: { english: 'Exotic', color: '#d85cff' },
  非凡: { english: 'Extraordinary', color: '#ef5b69' },
}

const categoryTabs = [
  { label: '全部', value: 'all' },
  { label: '武器箱', value: 'case' },
  { label: '纪念包 / 收藏', value: 'souvenir' },
  { label: '印花 / 布章', value: 'capsule' },
  { label: '补充规则', value: 'special' },
]

const probabilitySections = [
  {
    type: 'case',
    title: '第一类',
    subtitle: '需要钥匙开启的武器箱',
    note: '武器箱固定包含军规级到隐秘级，以及罕见特殊物品档位。',
    odds: [
      ['军规级', '79.923%'],
      ['受限', '15.985%'],
      ['保密', '3.197%'],
      ['隐秘', '0.639%'],
      ['罕见特殊物品', '0.256%'],
    ],
  },
  {
    type: 'souvenir',
    title: '第二类',
    subtitle: '含有消费级、工业级、军规级品质皮肤的 Major 纪念包',
    odds: [
      ['消费级', '80.537%'],
      ['工业级', '16.107%'],
      ['军规级', '3.356%'],
    ],
  },
  {
    type: 'souvenir',
    title: '第三类',
    subtitle: '含有工业级、军规级、受限品质皮肤的 Major 纪念包',
    odds: [
      ['工业级', '80.000%'],
      ['军规级', '16.667%'],
      ['受限', '3.333%'],
    ],
  },
  {
    type: 'souvenir',
    title: '第四类',
    subtitle: '含有消费级、工业级、军规级、受限品质皮肤的 Major 纪念包',
    odds: [
      ['消费级', '80.000%'],
      ['工业级', '16.000%'],
      ['军规级', '3.333%'],
      ['受限', '0.667%'],
    ],
  },
  {
    type: 'souvenir',
    title: '第五类',
    subtitle: '含有消费级、工业级、军规级、受限、保密品质皮肤的 Major 纪念包',
    odds: [
      ['消费级', '79.893%'],
      ['工业级', '15.979%'],
      ['军规级', '3.329%'],
      ['受限', '0.666%'],
      ['保密', '0.133%'],
    ],
  },
  {
    type: 'souvenir',
    title: '第六类',
    subtitle: '含有消费级、工业级、军规级、受限、保密、隐秘品质皮肤的 Major 纪念包、地图收藏包',
    odds: [
      ['消费级', '79.872%'],
      ['工业级', '15.974%'],
      ['军规级', '3.328%'],
      ['受限', '0.666%'],
      ['保密', '0.133%'],
      ['隐秘', '0.027%'],
    ],
  },
  {
    type: 'capsule',
    title: '第七类',
    subtitle: '含有高级、卓越品质印花的胶囊、布章包',
    odds: [
      ['高级', '83.333%'],
      ['卓越', '16.667%'],
    ],
  },
  {
    type: 'capsule',
    title: '第八类',
    subtitle: '含有高级、卓越、奇异品质印花的胶囊、布章包',
    odds: [
      ['高级', '80.645%'],
      ['卓越', '16.129%'],
      ['奇异', '3.226%'],
    ],
  },
  {
    type: 'capsule',
    title: '第九类',
    subtitle: '含有卓越、奇异品质印花的胶囊',
    odds: [
      ['卓越', '83.333%'],
      ['奇异', '16.667%'],
    ],
  },
  {
    type: 'capsule',
    title: '第十类',
    subtitle: '含有高级、卓越、奇异、非凡品质印花的胶囊',
    odds: [
      ['高级', '80.128%'],
      ['卓越', '16.026%'],
      ['奇异', '3.205%'],
      ['非凡', '0.641%'],
    ],
  },
  {
    type: 'capsule',
    title: '第十一类',
    subtitle: '含有高级、奇异品质印花的胶囊',
    odds: [
      ['高级', '96.154%'],
      ['奇异', '3.846%'],
    ],
  },
  {
    type: 'capsule',
    title: '第十二类',
    subtitle: '含有高级、卓越、非凡品质印花的胶囊',
    odds: [
      ['高级', '82.782%'],
      ['卓越', '16.556%'],
      ['非凡', '0.662%'],
    ],
  },
  {
    type: 'capsule',
    title: '第十三类',
    subtitle: '仅含有一种品质印花的胶囊或者音乐盒集',
    note: '其中每个道具的中奖概率相同。',
    odds: [],
  },
  {
    type: 'special',
    title: '陶瓷胶囊',
    subtitle: '本级与低一级品质物品的概率为 1:2',
    odds: [
      ['高级', '53.333%'],
      ['卓越', '26.667%'],
      ['奇异', '13.333%'],
      ['非凡', '6.667%'],
    ],
  },
  {
    type: 'special',
    title: '武库通行证补充',
    subtitle: '迷藏宝件、袖珍武器挂件、元素手作印花、角色手作印花',
    note: '同一道具池中，相同品质的不同物品获取概率相同。',
    odds: [
      ['高级', '80.128%'],
      ['卓越', '16.026%'],
      ['奇异', '3.205%'],
      ['非凡', '0.641%'],
    ],
  },
  {
    type: 'special',
    title: '收藏品补充',
    subtitle: '死亡游乐园 2024、平面设计收藏品、狩猎运动收藏品',
    odds: [
      ['工业级', '80.026%'],
      ['军规级', '16.005%'],
      ['受限', '3.201%'],
      ['保密', '0.640%'],
      ['隐秘', '0.128%'],
    ],
  },
]

const selectedType = ref('all')

const visibleSections = computed(() => {
  if (selectedType.value === 'all') {
    return probabilitySections
  }
  return probabilitySections.filter((section) => section.type === selectedType.value)
})

const qualityStyle = (quality) => ({
  color: QUALITY_META[quality]?.color || '#edf1f7',
})
</script>

<template>
  <section class="odds-gallery page-panel reveal-up">
    <div class="odds-control-bar operation-panel">
      <div>
        <span class="section-kicker">概率规则</span>
        <h2>容器概率图鉴</h2>
        <p class="surface-note">按武器箱、Major 纪念包、收藏包、胶囊、布章包和武库补充规则展示中奖概率。</p>
      </div>
      <el-segmented :model-value="selectedType" :options="categoryTabs" @change="selectedType = $event" />
    </div>

    <div class="odds-reference-grid">
      <article v-for="section in visibleSections" :key="`${section.title}-${section.subtitle}`" class="odds-category-card operation-panel">
        <div class="odds-category-head">
          <span>{{ section.title }}</span>
          <strong>{{ section.subtitle }}</strong>
        </div>

        <div v-if="section.odds.length" class="odds-prob-table" :style="{ '--odds-columns': section.odds.length }">
          <div class="odds-table-label">品质</div>
          <div
            v-for="[quality] in section.odds"
            :key="`${section.title}-${quality}`"
            class="odds-quality-cell"
            :style="qualityStyle(quality)"
          >
            {{ quality }}
          </div>
          <div class="odds-table-label">中奖概率</div>
          <div
            v-for="[quality, odds] in section.odds"
            :key="`${section.title}-${quality}-${odds}`"
            class="odds-value-cell"
          >
            {{ odds }}
          </div>
        </div>

        <div v-else class="odds-even-rule">
          <strong>同品质均分</strong>
          <span>该类型只含一种品质，所有道具概率相同。</span>
        </div>

        <p v-if="section.note" class="surface-note">{{ section.note }}</p>
      </article>
    </div>

    <div class="odds-footnote operation-panel">
      <span class="section-kicker">温馨提示</span>
      <p>
        相应概率均是在大样本统计数据中的估计数值，与单个玩家的少量测试数据之间可能会存在一定差异。
        希望亲爱的玩家适度游戏，理性消费，健康娱乐，谨防受骗。
      </p>
    </div>
  </section>
</template>
