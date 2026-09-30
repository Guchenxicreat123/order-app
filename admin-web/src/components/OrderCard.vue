<template>
  <div
    class="order-card"
    :class="'status-' + order.status"
    @click="$emit('open', order.orderId)"
  >
    <div class="oc-head">
      <div class="oc-no">📦 订单 #{{ order.orderId }}</div>
      <div class="oc-status" :class="'st-' + order.status">{{ statusText(order.status) }}</div>
    </div>
    <div class="oc-meta">
      <span class="oc-user">👤 {{ order.userNickname }}</span>
      <span class="oc-dot">·</span>
      <span>{{ formatTime(order.createdAt) }}</span>
    </div>
    <div class="oc-stats">
      <span class="ocs-item">{{ order.itemCount }} 道菜</span>
      <span class="ocs-dot">·</span>
      <span class="ocs-item">
        <span class="ocs-conf">{{ order.confirmedCount }}</span>/{{ order.itemCount }} 已确认
      </span>
      <span class="ocs-dot">·</span>
      <span class="ocs-item" v-if="order.rejectedCount">
        <span class="ocs-rej">{{ order.rejectedCount }}</span> 已驳回
      </span>
    </div>
    <div class="oc-foot">
      <span class="oc-total">¥{{ formatMoney(order.totalAmount) }}</span>
      <span class="oc-arrow">点击查看 ›</span>
    </div>
    <div class="oc-btns" @click.stop>
      <el-button type="primary" size="small" plain @click="$emit('edit', order)">✏️ 修改</el-button>
      <el-button
        type="danger"
        size="small"
        plain
        :loading="deleting"
        @click="$emit('delete', order)"
      >🗑️ 删除</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 单笔订单 */
  order: { type: Object, required: true },
  /** 是否处于删除 loading */
  deleting: { type: Boolean, default: false }
})

defineEmits(['open', 'edit', 'delete'])

const statusText = (s) => {
  if (s === 1) return '已确认'
  if (s === 2) return '已驳回'
  if (s === -1) return '已撤销'
  return '待确认'
}

const formatTime = (ts) => {
  if (!ts) return ''
  return ts.length >= 16 ? ts.substring(5, 16) : ts
}
const formatMoney = (n) => parseFloat(n || 0).toFixed(2)
</script>

<style scoped>
.order-card {
  background: white; border-radius: 16px; padding: 20px;
  cursor: pointer; box-shadow: 0 2px 12px rgba(0,0,0,0.06);
  transition: all 0.2s;
  border-top: 4px solid #f0e6dc;
}
.order-card:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0,0,0,0.1); }
.order-card.status-1 { border-top-color: #27ae60; }
.order-card.status-0 { border-top-color: #FF6B35; }
.order-card.status-2 { border-top-color: #c0392b; }
.oc-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.oc-no { font-size: 16px; font-weight: 700; color: #3d2314; }
.oc-status {
  font-size: 12px; padding: 2px 10px; border-radius: 12px; font-weight: 600;
}
.oc-status.st-0 { background: #fff3e0; color: #e67e22; }
.oc-status.st-1 { background: #e8f5e9; color: #27ae60; }
.oc-status.st-2 { background: #fee; color: #c0392b; }
.oc-status.st--1 { background: #f5f5f5; color: #999; }

.oc-meta { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #8b6f5c; margin-bottom: 10px; }
.oc-user { font-weight: 600; color: #3d2314; }
.oc-dot { color: #c4a882; }
.oc-stats { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #8b6f5c; margin-bottom: 12px; }
.ocs-item { background: #faf8f5; padding: 2px 10px; border-radius: 10px; }
.ocs-conf { font-weight: 700; color: #27ae60; }
.ocs-rej { font-weight: 700; color: #c0392b; }
.ocs-dot { color: #c4a882; }

.oc-foot { display: flex; justify-content: space-between; align-items: center;
  padding-top: 12px; border-top: 1px solid #f0e6dc; }
.oc-total { font-size: 20px; font-weight: 700; color: #FF6B35; }
.oc-arrow { font-size: 12px; color: #c4a882; }
.oc-btns { display: flex; justify-content: flex-end; gap: 8px;
  margin-top: 10px; padding-top: 10px; border-top: 1px solid #f0e6dc; }

@media (max-width: 767px) {
  .order-card { padding: 16px; }
  .oc-no { font-size: 15px; }
  .oc-total { font-size: 18px; }
}
</style>
