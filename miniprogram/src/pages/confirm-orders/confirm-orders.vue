<template>
  <view class="confirm-page page-bg-ethereal">
    <CustomNav title="订单确认" tone="glass" />

    <view class="header">
      <text class="sub">待确认 {{ pendingCount }} 单 · 共 {{ orders.length }} 单</text>
    </view>

    <view v-if="loading && orders.length === 0" class="loading-mask">
      <SkeletonBlock variant="spoon" height="160rpx" width="240rpx" text="掌勺中…" />
    </view>

    <view v-else-if="error" class="empty-state glass-card anim-fade-up">

        <view class="empty-illus"><Icon name="bell" size="120rpx" tone="primary" class="empty-icon" /></view>
      <text class="empty-title">加载失败</text>
      <text class="empty-hint">检查网络后点击重试</text>
      <button class="btn-primary go-btn pressable" @tap="loadOrders">点击重试</button>
    </view>

    <view v-else-if="orders.length === 0" class="empty-state glass-card anim-fade-up">

        <view class="empty-illus"><Icon name="bell" size="120rpx" tone="primary" class="empty-icon" /></view>
      <text class="empty-title">锅里还没有新菜</text>
      <text class="empty-hint">家人下完单后，菜会出现在这里</text>
    </view>

    <!-- 订单卡片列表 -->
    <view v-else class="order-list">
      <view
        v-for="o in orders"
        :key="o.orderId"
        class="order-card glass-card pressable"
      >
        <view class="oc-head" @tap="openDetail(o.orderId)">
          <view class="oc-head-left">
            <Icon name="cart" size="22rpx" tone="primary" />
            <text class="oc-no">订单 #{{ o.orderId }}</text>
            <text class="oc-time">{{ fmtTime(o.createdAt) }}</text>
          </view>
          <view class="oc-status" :class="'st-' + o.status">
            {{ statusText(o.status) }}
          </view>
        </view>
        <view class="oc-meta" @tap="openDetail(o.orderId)">
          <Icon name="user" size="22rpx" tone="primary" />
          <text class="oc-user">{{ o.userNickname }}</text>
          <text class="oc-dot">·</text>
          <text class="oc-time-inline">{{ fmtTime(o.createdAt) }}</text>
        </view>
        <view class="oc-stats" @tap="openDetail(o.orderId)">
          <text class="oc-stat">{{ o.itemCount }} 道菜</text>
          <text class="oc-stat ok">已确认 {{ o.confirmedCount }}</text>
          <text class="oc-stat no">已驳回 {{ o.rejectedCount || 0 }}</text>
          <text class="oc-stat wait">待确认 {{ pendingItemCount(o) }}</text>
        </view>
        <view class="oc-foot" @tap="openDetail(o.orderId)">
          <text class="oc-total">¥{{ fmtMoney(o.totalAmount) }}</text>
          <text class="oc-arrow">查看详情 ›</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { getMyOrders } from '../../utils/request.js'
import { warmPushConfig } from '../../utils/subscribe.js'
import Icon from '../../components/Icon.vue'
import TapBurst from '../../components/TapBurst.vue'
import SkeletonBlock from '../../components/SkeletonBlock.vue'
import CustomNav from '../../components/CustomNav.vue'

const loading = ref(false)
const error = ref(false)
const orders = ref([])

const pendingCount = computed(() => orders.value.filter(o => o.status === 0).length)

// 菜品级待确认数 = 总数 - 已确认 - 已驳回
const pendingItemCount = (o) =>
  Math.max(0, (o.itemCount || 0) - (o.confirmedCount || 0) - (o.rejectedCount || 0))

const statusText = (s) => {
  if (s === 1) return '已确认'
  if (s === -1) return '已撤销'
  return '待确认'
}
const fmtMoney = (n) => (parseFloat(n || 0)).toFixed(2)
const fmtTime = (s) => {
  if (!s) return ''
  const d = new Date(s)
  if (isNaN(d)) return s
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}/${d.getDate()} ${p(d.getHours())}:${p(d.getMinutes())}`
}

const loadOrders = async () => {
  if (loading.value) return
  loading.value = true
  error.value = false
  try {
    orders.value = (await getMyOrders()) || []
  } catch (e) {
    orders.value = []
    error.value = true
  } finally {
    loading.value = false
  }
}

const openDetail = (id) => uni.navigateTo({ url: `/pages/order/detail?id=${id}` })

onShow(() => { loadOrders(); warmPushConfig('CHEF') })
onPullDownRefresh(async () => { await loadOrders(); uni.stopPullDownRefresh() })
</script>

<style scoped>
/* ============= 甜美少女粉·订单确认 ============= */
.confirm-page { padding-bottom: 10%; }

.header {
  padding: 2% 5% 6%;
  color: var(--c-text);
}
.sub { font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); letter-spacing: 1rpx; }

.loading-mask {
  padding: 13% 0;
  display: flex; flex-direction: column; align-items: center; gap: 3%;
}

.empty-state {
  margin: 4% 4% 0;
  text-align: center;
  padding: 16% 7%;
  border-radius: 32rpx;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  box-shadow: var(--glow-soft);
}
.empty-icon { display: block; margin: 0 auto 4%; }
.empty-title { display: block; font-size: var(--t-lg); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.empty-hint  { display: block; font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); margin-top: 2%; }

.order-list {
  padding: 0 4%;
  display: flex; flex-direction: column;
  gap: 24rpx;
}
.order-card {
  border-radius: 32rpx;
  padding: 4% 5%;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  box-shadow: var(--glow-soft);
}
.oc-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 2.5%; }
.oc-head-left { display: flex; align-items: center; gap: 1.5%; }
.oc-no { font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.oc-time { font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2); }
.oc-status {
  font-size: 22rpx; padding: 4rpx 14rpx; border-radius: 999rpx;
  font-weight: 400;
  white-space: nowrap;
}
.oc-status.st-0  { background: #FFF0F3; color: #B4637A; }
.oc-status.st-1  { background: #E3F7EE; color: var(--c-success); }
.oc-status.st--1 { background: var(--c-danger-bg); color: var(--c-danger); }

.oc-meta {
  display: flex; align-items: center; gap: 1.5%;
  font-size: var(--t-sm); font-weight: 300;
  color: var(--c-text-2); margin-bottom: 2%;
}
.oc-user { font-weight: 500; color: var(--c-deep-rose); }
.oc-dot { color: var(--c-text-3); }
.oc-time-inline { color: var(--c-text-2); font-weight: 300; }

/* 订单菜品数量统计 */
.oc-stats {
  display: flex; align-items: center; flex-wrap: wrap; gap: 2%;
  margin: 2% 0;
  padding: 3% 4%;
  border-radius: 16rpx;
  background: rgba(255,255,255,0.4);
  border: 1rpx solid rgba(255,255,255,0.4);
  font-size: 24rpx;
  font-weight: 400;
}
.oc-stat { white-space: nowrap; color: var(--c-deep-rose); font-weight: 400; }
.oc-stat.ok   { color: var(--c-success); }
.oc-stat.no   { color: var(--c-danger); }
.oc-stat.wait { color: #B4637A; }

.oc-foot {
  display: flex; justify-content: space-between; align-items: center;
  padding-top: 3%; border-top: 1rpx solid rgba(122,74,90,.18);
}
.oc-total { font-size: var(--t-lg); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.oc-arrow { font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2); }
</style>