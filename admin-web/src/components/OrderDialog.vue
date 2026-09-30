<template>
  <el-dialog
    :model-value="visible"
    :width="windowWidth < 768 ? '95%' : '640px'"
    :fullscreen="windowWidth < 480"
    :show-close="true"
    :close-on-click-modal="true"
    class="order-detail-dialog"
    @update:model-value="$emit('update:visible', $event)"
    @close="$emit('close')"
  >
    <template #header>
      <div class="dialog-header" v-if="detail">
        <div class="dh-title">订单 #{{ detail.orderId }}</div>
        <div class="dh-status" :class="'st-' + detail.status">{{ statusText(detail.status) }}</div>
      </div>
    </template>

    <div v-if="loading" class="loading-tip">加载中...</div>

    <div v-else-if="detail">
      <!-- 概览 -->
      <div class="dh-overview">
        <div class="dh-stat">
          <div class="dh-stat-num">{{ detail.itemCount }}</div>
          <div class="dh-stat-lab">道菜</div>
        </div>
        <div class="dh-divider"></div>
        <div class="dh-stat">
          <div class="dh-stat-num">¥{{ formatMoney(detail.totalAmount) }}</div>
          <div class="dh-stat-lab">合计</div>
        </div>
        <div class="dh-divider"></div>
        <div class="dh-stat">
          <div class="dh-stat-num">{{ confirmedOfDetail }}/{{ detail.items.length }}</div>
          <div class="dh-stat-lab">已确认</div>
        </div>
        <div class="dh-divider"></div>
        <div class="dh-stat">
          <div class="dh-stat-num">{{ rejectedOfDetail }}</div>
          <div class="dh-stat-lab">已驳回</div>
        </div>
      </div>

      <!-- 下单人 -->
      <div class="dh-user">
        <span class="dh-emoji">👤</span>
        <span class="dh-user-name">{{ detail.userNickname }}</span>
        <span class="dh-user-time">{{ detail.createdAt }}</span>
      </div>

      <!-- 备注 -->
      <div v-if="detail.remark" class="dh-remark">📝 {{ detail.remark }}</div>

      <!-- 一键确认按钮 -->
      <div class="dh-actions-bar" v-if="canOperate">
        <el-button
          v-if="detail.status === 0"
          type="success"
          size="default"
          :loading="confirmingAll"
          @click="$emit('confirm-all')"
        >✅ 一键确认全部</el-button>
        <el-tag v-else type="success" effect="dark" size="large">✓ 已全部确认</el-tag>
      </div>
      <el-alert
        v-else
        title="您不是本家庭的主厨或创建者，无法确认/驳回菜品"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px;"
      />

      <!-- 菜品列表 -->
      <div class="dh-title-section">菜品一览 ({{ detail.items.length }})</div>
      <div
        v-for="it in detail.items"
        :key="it.itemId"
        class="dh-item"
        :class="{
          'st-confirmed': it.status === 1,
          'st-rejected': it.status === 2,
          'st-cancelled': it.status === -1
        }"
      >
        <span class="dhi-emoji">{{ it.dishEmoji || (it.dishId ? '🍱' : '🍳') }}</span>
        <div class="dhi-body">
          <div class="dhi-name">{{ it.dishName }}</div>
          <div class="dhi-meta">
            <span class="dhi-tag" :class="{ custom: !it.dishId }">{{ it.dishId ? '已有菜单' : '自定义' }}</span>
            <span v-if="it.spiceLevel === 1" class="dhi-spice">🌶️微辣</span>
            <span v-else-if="it.spiceLevel === 2" class="dhi-spice">🌶️重辣</span>
            <span class="dhi-status-text" :class="'st-' + it.status">{{ statusText(it.status) }}</span>
          </div>
          <div v-if="it.remark" class="dhi-remark">💬 {{ it.remark }}</div>
        </div>
        <div class="dhi-right">
          <div class="dhi-price">¥{{ formatMoney(it.price) }}</div>
          <div class="dhi-actions" v-if="canOperate && it.status !== -1">
            <el-button
              v-if="it.status !== 1"
              type="success"
              size="small"
              :loading="confirmingItem === it.itemId"
              @click.stop="$emit('confirm-item', it.itemId)"
            >确认</el-button>
            <el-button
              v-if="it.status !== 2"
              type="danger"
              size="small"
              plain
              :loading="rejectingItem === it.itemId"
              @click.stop="$emit('reject-item', it.itemId)"
            >驳回</el-button>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  detail: { type: Object, default: null },
  canOperate: { type: Boolean, default: false },
  confirmingAll: { type: Boolean, default: false },
  confirmingItem: { type: [Number, String], default: null },
  rejectingItem: { type: [Number, String], default: null }
})

defineEmits(['update:visible', 'close', 'confirm-all', 'confirm-item', 'reject-item'])

const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1280)
const handleResize = () => { windowWidth.value = window.innerWidth }
onMounted(() => window.addEventListener('resize', handleResize))
onUnmounted(() => window.removeEventListener('resize', handleResize))

const confirmedOfDetail = computed(() =>
  props.detail ? props.detail.items.filter(i => i.status === 1).length : 0
)
const rejectedOfDetail = computed(() =>
  props.detail ? (props.detail.rejectedCount ?? props.detail.items.filter(i => i.status === 2).length) : 0
)

const statusText = (s) => {
  if (s === 1) return '已确认'
  if (s === 2) return '已驳回'
  if (s === -1) return '已撤销'
  return '待确认'
}
const formatMoney = (n) => parseFloat(n || 0).toFixed(2)
</script>

<style scoped>
.dialog-header { display: flex; justify-content: space-between; align-items: center; width: 100%; }
.dh-title { font-size: 18px; font-weight: 700; color: #3d2314; }
.dh-status {
  font-size: 13px; padding: 4px 14px; border-radius: 14px; font-weight: 600;
}
.dh-status.st-0 { background: #fff3e0; color: #e67e22; }
.dh-status.st-1 { background: #e8f5e9; color: #27ae60; }
.dh-status.st-2 { background: #fee; color: #c0392b; }
.dh-status.st--1 { background: #f5f5f5; color: #999; }

.loading-tip { text-align: center; padding: 40px; color: #8b6f5c; }

.dh-overview {
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #FFFAF5, #fff3e0); border-radius: 12px;
  padding: 16px; margin-bottom: 16px;
}
.dh-stat { flex: 1; text-align: center; }
.dh-stat-num { font-size: 22px; font-weight: 700; color: #FF6B35; }
.dh-stat-lab { font-size: 12px; color: #8b6f5c; margin-top: 2px; }
.dh-divider { width: 1px; height: 32px; background: #f0e6dc; margin: 0 8px; }

.dh-user {
  display: flex; align-items: center; gap: 8px;
  background: white; border: 1px solid #f0e6dc; border-radius: 10px;
  padding: 10px 14px; margin-bottom: 12px;
}
.dh-emoji { font-size: 24px; }
.dh-user-name { font-size: 15px; font-weight: 600; color: #3d2314; flex: 1; }
.dh-user-time { font-size: 12px; color: #8b6f5c; }

.dh-remark {
  background: #faf8f5; border-radius: 8px; padding: 10px 14px;
  margin-bottom: 12px; font-size: 13px; color: #8b6f5c;
}

.dh-actions-bar { margin-bottom: 12px; display: flex; gap: 8px; }

.dh-title-section {
  font-size: 14px; font-weight: 700; color: #3d2314; margin: 16px 0 10px;
  padding-bottom: 8px; border-bottom: 1px solid #f0e6dc;
}

.dh-item {
  display: flex; align-items: center; gap: 12px;
  padding: 12px; border-radius: 10px; margin-bottom: 8px;
  background: #faf8f5; transition: all 0.2s;
}
.dh-item:last-child { margin-bottom: 0; }
.dh-item.st-confirmed { background: #f6fbf6; border-left: 3px solid #27ae60; }
.dh-item.st-rejected { background: linear-gradient(90deg, rgba(192,57,43,0.06), transparent); border-left: 3px solid #c0392b; }
.dh-item.st-cancelled { opacity: 0.5; text-decoration: line-through; }

.dhi-emoji { font-size: 32px; flex-shrink: 0; }
.dhi-body { flex: 1; min-width: 0; }
.dhi-name { font-size: 15px; font-weight: 600; color: #3d2314; }
.dhi-meta { display: flex; align-items: center; gap: 8px; margin-top: 4px; flex-wrap: wrap; }
.dhi-tag {
  font-size: 11px; padding: 1px 8px; border-radius: 6px;
  background: #fff3e0; color: #e67e22;
}
.dhi-tag.custom { background: #e8f5e9; color: #27ae60; }
.dhi-spice { font-size: 11px; color: #e67e22; }
.dhi-status-text {
  font-size: 11px; padding: 1px 8px; border-radius: 6px; font-weight: 600;
}
.dhi-status-text.st-0 { background: #fff3e0; color: #e67e22; }
.dhi-status-text.st-1 { background: #e8f5e9; color: #27ae60; }
.dhi-status-text.st-2 { background: #fee; color: #c0392b; }
.dhi-status-text.st--1 { background: #f5f5f5; color: #999; }
.dhi-remark { font-size: 12px; color: #8b6f5c; margin-top: 4px; font-style: italic; }

.dhi-right { display: flex; flex-direction: column; align-items: flex-end; gap: 6px; flex-shrink: 0; }
.dhi-price { font-size: 15px; font-weight: 700; color: #FF6B35; }
.dhi-actions { display: flex; gap: 4px; }
</style>
