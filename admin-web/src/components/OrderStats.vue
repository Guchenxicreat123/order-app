<template>
  <!-- 统计行 -->
  <div class="stats-row">
    <div class="stat-card stat-orders">
      <div class="stat-emoji">📦</div>
      <div class="stat-info">
        <div class="stat-num">{{ orders.length }}</div>
        <div class="stat-label">订单数</div>
      </div>
    </div>
    <div class="stat-card">
      <div class="stat-emoji">🍽️</div>
      <div class="stat-info">
        <div class="stat-num">{{ totalItems }}</div>
        <div class="stat-label">总菜数</div>
      </div>
    </div>
    <div class="stat-card stat-confirmed">
      <div class="stat-emoji">✅</div>
      <div class="stat-info">
        <div class="stat-num">{{ confirmedItems }}</div>
        <div class="stat-label">已确认</div>
      </div>
    </div>
    <div class="stat-card stat-rejected">
      <div class="stat-emoji">❌</div>
      <div class="stat-info">
        <div class="stat-num">{{ rejectedItems }}</div>
        <div class="stat-label">已驳回</div>
      </div>
    </div>
    <div class="stat-card stat-amount">
      <div class="stat-emoji">💰</div>
      <div class="stat-info">
        <div class="stat-num">¥{{ totalAmount }}</div>
        <div class="stat-label">合计</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 订单列表：[{ itemCount, confirmedCount, rejectedCount, totalAmount, ... }] */
  orders: { type: Array, default: () => [] }
})

const totalItems = computed(() => props.orders.reduce((s, o) => s + (o.itemCount || 0), 0))
const confirmedItems = computed(() => props.orders.reduce((s, o) => s + (o.confirmedCount || 0), 0))
const rejectedItems = computed(() => props.orders.reduce((s, o) => s + (o.rejectedCount || 0), 0))
const totalAmount = computed(() =>
  props.orders.reduce((s, o) => s + parseFloat(o.totalAmount || 0), 0).toFixed(2)
)
</script>

<style scoped>
.stats-row {
  display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; margin-bottom: 20px;
}
.stat-card {
  background: white; border-radius: 12px; padding: 16px;
  display: flex; align-items: center; gap: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  border-left: 4px solid #FF6B35;
}
.stat-card.stat-confirmed { border-left-color: #27ae60; }
.stat-card.stat-rejected { border-left-color: #c0392b; }
.stat-card.stat-amount { border-left-color: #FFB84D; }
.stat-card.stat-orders { border-left-color: #3498db; }
.stat-emoji { font-size: 32px; }
.stat-info { flex: 1; }
.stat-num { font-size: 20px; font-weight: 700; color: #3d2314; }
.stat-label { font-size: 12px; color: #8b6f5c; margin-top: 2px; }
@media (max-width: 900px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 767px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); gap: 8px; }
  .stat-card { padding: 12px; }
  .stat-num { font-size: 18px; }
  .stat-emoji { font-size: 26px; }
}
</style>
