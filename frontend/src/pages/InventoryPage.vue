<script setup>
import InventoryBoard from '../components/InventoryBoard.vue'

defineProps({
  loadingInventory: {
    type: Boolean,
    required: true,
  },
  inventoryState: {
    type: Object,
    required: true,
  },
  inventoryStats: {
    type: Array,
    required: true,
  },
  inventoryItems: {
    type: Array,
    required: true,
  },
})

defineEmits(['restore-inventory', 'go-data', 'page-change'])
</script>

<template>
  <section class="page-panel reveal-up">
    <div class="page-toolbar">
      <div>
        <span class="section-kicker">库存看板</span>
        <h2>库存看板</h2>
        <p class="surface-note">只展示数据库中最近一次保存的武器库存。需要重新抓取时，请到“数据”页启动后台任务。</p>
      </div>
      <div class="inline-actions">
        <el-button plain :loading="loadingInventory" @click="$emit('restore-inventory')">刷新看板</el-button>
        <el-button type="warning" @click="$emit('go-data')">去数据页采集</el-button>
      </div>
    </div>
    <p class="surface-note toolbar-note">{{ inventoryState.lastAction }}</p>

    <InventoryBoard
      :inventory-stats="inventoryStats"
      :inventory-items="inventoryItems"
      :current-page="inventoryState.currentPage"
      :page-size="inventoryState.pageSize"
      :total-items="inventoryState.usePersistedPaging ? inventoryState.totalItems : inventoryItems.length"
      :use-persisted-paging="inventoryState.usePersistedPaging"
      @page-change="$emit('page-change', $event)"
    />
  </section>
</template>
