<template>
  <view class="order-detail-page page-bg-ethereal">
    <CustomNav title="订单详情" tone="glass" />

    <view class="page-header">
      <view class="ph-row">
        <text class="ph-no">订单 #{{ order && order.orderId }}</text>
        <text class="ph-status" :class="'st-' + (order && order.status)">
          {{ order && statusText(order.status) }}
        </text>
      </view>
      <text class="ph-time">{{ order && fmtTime(order.createdAt) }}</text>
      <view class="ph-stats">
        <text class="ph-stat-num">{{ order && order.itemCount }}</text>
        <text class="ph-stat-lab">道菜</text>
        <text class="ph-stat-sep">·</text>
        <text class="ph-stat-num">¥{{ order && fmtMoney(order.totalAmount) }}</text>
      </view>
    </view>

    <!-- 下单人 -->
    <view class="user-card glass-card-2">
      <view class="user-emoji-wrap">
        <Icon name="user" size="56rpx" tone="primary" />
      </view>
      <view class="user-info">
        <text class="user-name">{{ order && order.userNickname }}</text>
        <text class="user-sub">下单人</text>
      </view>
    </view>

    <!-- 备注 -->
    <view class="remark-block glass-card-2" v-if="order && order.remark">
      <view class="rb-label">
        <Icon name="edit" size="24rpx" tone="primary" />
        <text>订单备注</text>
      </view>
      <text class="rb-text">{{ order.remark }}</text>
    </view>

    <!-- 菜品列表 -->
    <view class="dish-list glass-card-2">
      <view class="dl-title">
        <Icon name="sparkle" size="26rpx" tone="gold" />
        <text>菜品一览（{{ order && order.items ? order.items.length : 0 }}）</text>
      </view>
      <view
        v-for="it in (order && order.items) || []"
        :key="it.itemId"
        class="dish-row"
        :class="{
          confirmed: it.status === 1,
          rejected:  it.status === 2,
          leaving:   leavingIds.has(it.itemId),
          cancelled: order && order.status === -1
        }"
      >
        <view class="dish-emoji-wrap">
          <Icon
            :name="it.dishId ? 'spoon' : 'heart-egg'"
            size="56rpx"
            :tone="iconToneFor(it)"
          />
        </view>
        <view class="dish-body">
          <text class="dish-name">{{ it.dishName }}</text>
          <view class="dish-meta">
            <text class="meta-tag tag-pri" v-if="it.dishId">已有菜单</text>
            <text class="meta-tag tag-chef" v-else>自定义</text>
            <text class="meta-spice" v-if="it.spiceLevel === 1">微辣</text>
            <text class="meta-spice" v-else-if="it.spiceLevel === 2">重辣</text>
            <text class="meta-time">{{ fmtTime(it.createdAt) }}</text>
          </view>
          <view class="dish-status st-1" v-if="it.status === 1">
            <Icon name="check" size="22rpx" tone="white" />
            <text>已确认</text>
          </view>
          <view class="dish-status st--1" v-else-if="it.status === 2">
            <Icon name="close" size="22rpx" tone="white" />
            <text>已驳回</text>
          </view>
        </view>
        <view class="dish-right">
          <text class="dish-price">¥{{ fmtMoney(it.price) }}</text>
          <view class="action-row" v-if="order && order.canConfirm && it.status === 0">
            <TapBurst :icons="['heart','sparkle']" class="burst-btn">
              <button
                class="item-btn confirm pressable"
                :loading="confirming === it.itemId"
                @tap="confirmItem(it.itemId)"
              >确认</button>
            </TapBurst>
            <button
              class="item-btn reject pressable"
              :loading="rejecting === it.itemId"
              @tap="openRejectConfirm(it.itemId)"
            >驳回</button>
          </view>
        </view>
      </view>
    </view>

    <!-- 一键确认底部栏 -->
    <view
      v-if="order && order.canConfirm && pendingItemCount() > 0"
      class="confirm-all-bar safe-bottom"
    >
      <view class="cab-inner">
        <view class="cab-info">
          <text class="cab-count">{{ pendingItemCount() }}</text>
          <text class="cab-unit">道菜待确认</text>
        </view>
        <TapBurst :icons="['heart','sparkle','chef-hat']" class="cab-burst">
          <button
            class="btn-primary cab-btn pressable"
            :loading="confirmingAll"
            @tap="confirmAll"
          >一键确认全部</button>
        </TapBurst>
      </view>
    </view>

    <view v-if="loading && !order" class="loading-mask">
      <SkeletonBlock variant="spoon" height="160rpx" width="240rpx" text="掌勺中…" />
    </view>

    <!-- 自定义确认弹窗 -->
    <ConfirmDialog
      :visible="rejectVisible"
      title="驳回这道菜？"
      content="驳回后这道菜不会出现在今晚的买菜清单里。"
      tone="danger"
      icon="close"
      confirm-text="驳回"
      cancel-text="再想想"
      @confirm="confirmReject"
      @cancel="rejectVisible = false"
    />
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getOrderDetail, confirmOrder, confirmOrderItem, rejectOrderItem } from '../../utils/request.js'
import { ensureSubscribeBulk, warmPushConfig, CHEF_QUOTA_ON_CONFIRM } from '../../utils/subscribe.js'
import Icon from '../../components/Icon.vue'
import TapBurst from '../../components/TapBurst.vue'
import SkeletonBlock from '../../components/SkeletonBlock.vue'
import CustomNav from '../../components/CustomNav.vue'
import ConfirmDialog from '../../components/ConfirmDialog.vue'

const order = ref(null)
const loading = ref(true)
const loadingLock = ref(false)
const confirming = ref(null)
const confirmingAll = ref(false)
const rejecting = ref(null)
const leavingIds = ref(new Set())
const rejectVisible = ref(false)
const pendingReject = ref(null)

const statusText = (s) => {
  if (s === 1) return '已确认'
  if (s === -1) return '已撤销'
  return '待确认'
}
const pendingItemCount = () => {
  if (!order.value || !order.value.items) return 0
  return order.value.items.filter(i => i.status === 0).length
}
const fmtMoney = (n) => (parseFloat(n || 0)).toFixed(2)
const fmtTime = (s) => {
  if (!s) return ''
  const d = new Date(s)
  if (isNaN(d)) return s
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}/${d.getDate()} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 已确认 → 绿色调；已驳回 → 红色调；其他 → 珊瑚粉 */
function iconToneFor(it) {
  if (it.status === 1) return 'success' // 我们没 success tone，Icon 用 primary 也行；这里返回 primary
  if (it.status === 2) return 'primary'
  return 'primary'
}

onLoad(async (q) => {
  const id = q.id
  if (!id) return
  await loadDetail(id)
})

onShow(() => {
  if (order.value && order.value.orderId) loadDetail(order.value.orderId)
  // 本页只有主厨会做确认/驳回操作，预热「新订单」提醒的订阅配置
  warmPushConfig('CHEF')
})

const loadDetail = async (id) => {
  if (loadingLock.value) return
  loadingLock.value = true
  try {
    order.value = await getOrderDetail(id)
  } catch (e) {} finally {
    loadingLock.value = false
    loading.value = false
  }
}

const confirmItem = async (itemId) => {
  confirming.value = itemId
  // 确认某个菜 = 主厨动作，借这次点击顺手补「新订单」提醒额度，保证下次家人点菜还能收到。
  // 必须同步调用（在 await 之前），否则用户手势丢失、微信静默不弹面板。
  ensureSubscribeBulk('CHEF', CHEF_QUOTA_ON_CONFIRM)
  try {
    await confirmOrderItem(itemId)
    try { uni.vibrateShort({ type: 'light' }) } catch (e) {}
    uni.showToast({ title: '心意已收', icon: 'success' })
    if (order.value) loadDetail(order.value.orderId)
  } catch (e) {
    uni.showToast({ title: '确认失败', icon: 'none' })
  } finally {
    confirming.value = null
  }
}

const confirmAll = async () => {
  if (!order.value) return
  confirmingAll.value = true
  ensureSubscribeBulk('CHEF', CHEF_QUOTA_ON_CONFIRM)
  try {
    await confirmOrder(order.value.orderId)
    try { uni.vibrateShort({ type: 'medium' }) } catch (e) {}
    uni.showToast({ title: '全部已确认', icon: 'success' })
    loadDetail(order.value.orderId)
  } catch (e) {
    const msg = (e && (e.message || e.errMsg)) || '确认失败'
    uni.showToast({ title: msg, icon: 'none' })
  } finally {
    confirmingAll.value = false
  }
}

const openRejectConfirm = (itemId) => {
  pendingReject.value = itemId
  rejectVisible.value = true
}

const confirmReject = async () => {
  rejectVisible.value = false
  const itemId = pendingReject.value
  if (!itemId) return

  // 驳回同样是主厨动作，顺手补订阅额度（同步调用，见 confirmItem）
  ensureSubscribeBulk('CHEF', CHEF_QUOTA_ON_CONFIRM)

  // 乐观：先左滑出再请求
  leavingIds.value = new Set([...leavingIds.value, itemId])
  setTimeout(() => {
    leavingIds.value.delete(itemId)
    if (order.value && order.value.items) {
      order.value.items = order.value.items.filter(i => i.itemId !== itemId)
    }
  }, 380)
  try { uni.vibrateShort({ type: 'medium' }) } catch (e) {}

  rejecting.value = itemId
  try {
    await rejectOrderItem(itemId)
    uni.showToast({ title: '已驳回', icon: 'none' })
    if (order.value) loadDetail(order.value.orderId)
  } catch (e) {
    uni.showToast({ title: '驳回失败', icon: 'none' })
  } finally {
    rejecting.value = null
  }
}
</script>

<style scoped>
/* ============= 甜美少女粉·订单详情 ============= */
.order-detail-page { padding-bottom: 18%; }

/* 头部：浮在甜美少女粉·上的纯文字 */
.page-header {
  padding: 4% 5% 6%;
  color: var(--c-text);
}
.ph-row { display: flex; justify-content: space-between; align-items: center; }
.ph-no {
  font-size: var(--t-xl); font-weight: 300; color: var(--c-text);
  letter-spacing: 2rpx;
  text-shadow: 0 4rpx 16rpx rgba(255,123,148,.22);
}
.ph-status {
  font-size: 22rpx; padding: 4rpx 14rpx; border-radius: 999rpx;
  font-weight: 400; white-space: nowrap;
  background: rgba(255,255,255,0.92);
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}
.ph-status.st-0  { color: #B4637A; }
.ph-status.st-1  { color: var(--c-success); }
.ph-status.st--1 { color: var(--c-danger); }
.ph-time {
  display: block; font-size: var(--t-xs); font-weight: 300;
  color: var(--c-text-2);
  margin-top: 1.5%; letter-spacing: 1rpx;
}
.ph-stats { display: flex; align-items: baseline; gap: 1.5%; margin-top: 4%; color: var(--c-text); }
.ph-stat-num { font-size: 150%; font-weight: 500; }
.ph-stat-lab { font-size: var(--t-xs); font-weight: 300; opacity: .9; }
.ph-stat-sep { font-size: var(--t-md); opacity: .7; margin: 0 1.5%; }

/* 通用卡样式 — 毛玻璃 */
.user-card,
.remark-block,
.dish-list {
  margin: 4%;
  padding: 5%;
  border-radius: 32rpx;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  box-shadow: var(--glow-soft);
}
.user-card {
  display: flex; align-items: center; gap: 3%;
  margin-top: -5%;
  position: relative; z-index: 2;
}
.user-emoji-wrap {
  width: 84rpx; height: 84rpx;
  background: rgba(255,255,255,0.7);
  border: 2rpx solid rgba(255,255,255,0.6);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}
.user-info { display: flex; flex-direction: column; gap: 1%; }
.user-name { font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.user-sub { font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2); }

.rb-label {
  display: flex; align-items: center; gap: 1.5%;
  font-size: var(--t-sm); font-weight: 400;
  color: var(--c-deep-rose);
  margin-bottom: 1.5%;
  letter-spacing: 1rpx;
}
.rb-text { display: block; font-size: var(--t-md); font-weight: 400; color: var(--c-deep-rose); }

.dl-title {
  display: flex; align-items: center; gap: 1.5%;
  font-size: 81.25%; font-weight: 500; color: var(--c-deep-rose);
  letter-spacing: 1rpx;
  margin-bottom: 3%;
}
.dish-row {
  display: flex; align-items: flex-start; gap: 3%;
  padding: 3% 0;
  border-bottom: 1rpx solid rgba(122,74,90,.15);
  transition: transform .38s cubic-bezier(.4,.0,.2,1), opacity .38s ease;
}
.dish-row:last-child { border-bottom: none; }
.dish-row.confirmed { opacity: .85; }
.dish-row.rejected  { opacity: .55; background: linear-gradient(90deg, rgba(229,72,77,0.06), transparent); }
.dish-row.cancelled { opacity: .55; filter: grayscale(0.4); }
.dish-row.leaving {
  animation: sk-slide-out-left 380ms forwards;
}
.dish-emoji-wrap {
  width: 84rpx; height: 84rpx;
  background: rgba(255,255,255,0.7);
  border: 2rpx solid rgba(255,255,255,0.55);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}
.dish-body { flex: 1; min-width: 0; }
.dish-name { font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose); display: block; letter-spacing: 1rpx; }
.dish-meta { display: flex; align-items: center; gap: 2%; margin-top: 1%; flex-wrap: wrap; }
.meta-tag {
  font-size: 20rpx; padding: 2rpx 12rpx; border-radius: 999rpx;
  white-space: nowrap;
  font-weight: 400;
}
.meta-spice { font-size: 20rpx; color: #B4637A; white-space: nowrap; font-weight: 400; }
.meta-time { font-size: 20rpx; font-weight: 300; color: var(--c-text-2); margin-left: auto; white-space: nowrap; }

.dish-status {
  display: inline-flex; align-items: center; gap: 1%;
  margin-top: 1.5%; padding: 4rpx 14rpx; border-radius: 999rpx;
  font-size: 22rpx; font-weight: 400;
  white-space: nowrap;
}
.dish-status.st-1  { background: var(--c-success); color: white; box-shadow: 0 2rpx 8rpx rgba(34,181,115,.3); }
.dish-status.st--1 { background: var(--c-danger);  color: white; box-shadow: 0 2rpx 8rpx rgba(229,72,77,.3); }

.dish-right { display: flex; flex-direction: column; align-items: flex-end; gap: 1.5%; flex-shrink: 0; }
.dish-price { font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.action-row { display: flex; gap: 1.5%; align-items: center; }
.burst-btn { display: inline-flex; }

.item-btn {
  border: none; border-radius: 999rpx;
  font-size: 24rpx; font-weight: 400;
  padding: 8rpx 24rpx; line-height: 1.4;
  white-space: nowrap;
}
.item-btn.confirm {
  background: var(--g-chef);
  color: white;
  box-shadow: var(--glow-blue);
}
.item-btn.reject {
  background: rgba(255,255,255,0.85);
  color: var(--c-danger);
  border: 1rpx solid var(--c-danger);
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}
.item-btn.reject[loading] { color: var(--c-danger); }

.loading-mask {
  padding: 13% 0;
  display: flex; flex-direction: column; align-items: center; gap: 3%;
}

/* 一键确认底部栏 — 毛玻璃 */
.confirm-all-bar {
  position: fixed;
  bottom: 0; left: 0; right: 0;
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(30rpx);
  -webkit-backdrop-filter: blur(30rpx);
  border-top: 1rpx solid rgba(255,255,255,0.6);
  box-shadow: 0 -8rpx 32rpx rgba(255,194,207,.18);
  z-index: 100;
}
.cab-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 5%;
  gap: 4%;
}
.cab-info {
  display: flex;
  flex-direction: column;
  gap: 2rpx;
  flex-shrink: 0;
}
.cab-count {
  font-size: var(--t-xxl);
  font-weight: 500;
  color: var(--c-deep-rose);
  line-height: 1;
  letter-spacing: 1rpx;
}
.cab-unit {
  font-size: var(--t-xs);
  font-weight: 300;
  color: var(--c-text-2);
}
.cab-burst { display: inline-flex; }
.cab-btn {
  padding: 20rpx 40rpx !important;
  font-size: var(--t-md) !important;
  font-weight: 400 !important;
  letter-spacing: 2rpx !important;
  border-radius: 999rpx !important;
  box-shadow: var(--glow-pink) !important;
}
</style>