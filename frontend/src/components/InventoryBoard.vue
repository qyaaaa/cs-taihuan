<script setup>
import { ref } from 'vue'

defineProps({
  inventoryStats: {
    type: Array,
    required: true,
  },
  groupedInventory: {
    type: Array,
    required: true,
  },
})

const inventoryView = ref('list')

const currency = (value) => `¥${Number(value || 0).toFixed(2)}`
</script>

<template>
  <section class="inventory-stage reveal-up">
    <div class="section-head section-head-row">
      <div>
        <span class="section-kicker">Inventory Board</span>
        <h2>炼金素材库存</h2>
      </div>
      <el-radio-group v-model="inventoryView" size="small" class="view-switch">
        <el-radio-button label="list">列表</el-radio-button>
        <el-radio-button label="card">卡片</el-radio-button>
      </el-radio-group>
    </div>

    <div class="metrics-row">
      <div v-for="metric in inventoryStats" :key="metric.label" class="metric-tile">
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
      </div>
    </div>

    <div v-if="inventoryView === 'list'" class="inventory-list">
      <div class="inventory-list-head">
        <span>饰品</span>
        <span>数量</span>
        <span>均价</span>
        <span>收藏品</span>
        <span>最低磨损</span>
      </div>
      <button
        v-for="group in groupedInventory"
        :key="group.name"
        class="inventory-row"
        type="button"
      >
        <span>
          <strong>{{ group.name }}</strong>
          <em>{{ group.rarity }}</em>
        </span>
        <span>{{ group.count }}</span>
        <span>{{ currency(group.avgPrice) }}</span>
        <span>{{ group.collection }}</span>
        <span>{{ group.minFloat === null ? '--' : group.minFloat.toFixed(4) }}</span>
      </button>
    </div>

    <div v-else class="inventory-cards">
      <article v-for="group in groupedInventory" :key="group.name" class="material-card">
        <div class="material-topline">
          <span>{{ group.rarity }}</span>
          <strong>x{{ group.count }}</strong>
        </div>
        <h3>{{ group.name }}</h3>
        <p>{{ group.collection }}</p>
        <div class="material-meta">
          <label>均价</label>
          <strong>{{ currency(group.avgPrice) }}</strong>
        </div>
        <div class="material-meta">
          <label>最低磨损</label>
          <strong>{{ group.minFloat === null ? '--' : group.minFloat.toFixed(4) }}</strong>
        </div>
      </article>
    </div>
  </section>
</template>

