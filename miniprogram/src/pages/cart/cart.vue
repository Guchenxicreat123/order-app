<template>
  <view class="cart-page page-bg-ethereal">
    <view class="header">
      <text class="sub">{{ cartItems.length }} 道菜 · 合计 ¥{{ totalAmount }}</text>
    </view>

    <!-- 加载 -->
    <view v-if="loading && cartItems.length === 0" class="loading-mask">
      <SkeletonBlock variant="spoon" height="160rpx" width="240rpx" text="掌勺中…" />
    </view>

    <!-- 空状态 -->
    <view v-else-if="error" class="empty-state glass-card anim-fade-up">

        <view class="empty-illus"><Icon name="heart-egg" size="120rpx" tone="primary" class="empty-icon" /></view>
      <text class="empty-title">加载失败</text>
      <text class="empty-hint">检查网络后点击重试</text>
      <button class="btn-primary go-btn pressable" @tap="onRetry">点击重试</button>
    </view>

    <view v-else-if="cartItems.length === 0" class="empty-state glass-card anim-fade-up">

        <view class="empty-illus"><Icon name="heart-egg" size="120rpx" tone="primary" class="empty-icon" /></view>
      <text class="empty-title">勺意还没攒齐今晚的菜</text>
      <text class="empty-hint">从首页点几道想吃的，菜会先落在这里</text>
      <button class="btn-primary go-btn pressable" @tap="goHome">去点菜</button>
    </view>

    <!-- 购物车列表 -->
    <view class="cart-list" v-else>
      <view
        v-for="item in cartItems"
        :key="item.id"
        class="cart-item glass-card"
        :class="{ 'anim-bounce-in': addedIds.has(item.id) }"
      >
        <view class="check-wrap" @tap="toggleItem(item.id)">
          <view class="checkbox" :class="{ checked: selectedIds.includes(item.id) }">
            <Icon v-if="selectedIds.includes(item.id)" name="check" size="22rpx" tone="white" />
          </view>
        </view>
        <view class="item-emoji-wrap">
          <Icon :name="item.dishId ? 'spoon' : 'heart-egg'" size="56rpx" tone="primary" />
        </view>
        <view class="item-body">
          <text class="item-name">{{ item.dishName }}</text>
          <view class="item-meta">
            <text class="tag tag-pri" v-if="item.dishId">已有菜单</text>
            <text class="tag tag-chef" v-else>自定义菜品</text>
            <text class="tag tag-warn spice-tag" v-if="item.spiceLevel === 1">微辣</text>
            <text class="tag tag-warn spice-tag" v-else-if="item.spiceLevel === 2">重辣</text>
          </view>
          <text class="item-hint" v-if="!item.dishId">这是你在「自定义菜品」页创建的</text>
        </view>
        <!-- 数量加减 -->
        <view class="qty-wrap">
          <TapBurst :icons="['trash']" :count="3">
            <view class="qty-btn pressable" @tap="changeQty(item, -1)">
              <Icon :name="item.quantity > 1 ? 'minus' : 'trash'" size="28rpx" tone="primary" />
            </view>
          </TapBurst>
          <view class="qty-num" @tap="openQtyEditor(item)">
            <text>{{ item.quantity }}</text>
          </view>
          <TapBurst :icons="['plus','heart']" :count="3">
            <view class="qty-btn pressable" @tap="changeQty(item, 1)">
              <Icon name="plus" size="28rpx" tone="primary" />
            </view>
          </TapBurst>
        </view>
        <view class="item-right">
          <text class="item-price">¥{{ (parseFloat(item.price || 0) * item.quantity).toFixed(2) }}</text>
        </view>
      </view>
    </view>

    <!-- 底部固定栏 -->
    <view class="bottom-bar safe-bottom" v-if="cartItems.length > 0">
      <view class="check-wrap" @tap="toggleAll">
        <view class="checkbox" :class="{ checked: allSelected }">
          <Icon v-if="allSelected" name="check" size="22rpx" tone="white" />
        </view>
        <text class="all-text">全选</text>
      </view>
      <view class="total-info">
        <text class="total-amount">¥{{ selectedTotal }}</text>
        <text class="total-count">已选 {{ selectedIds.length }} 道</text>
      </view>
      <TapBurst class="checkout-burst" :icons="['spoon','heart','tomato']">
        <button
          class="checkout-btn btn-primary pressable"
          @tap="openCheckout"
          :disabled="selectedIds.length === 0"
          :loading="checking"
        >去下单</button>
      </TapBurst>
    </view>

    <!-- 自定义确认弹窗（不再用原生 showModal） -->
    <ConfirmDialog
      :visible="checkoutVisible"
      title="把今晚的菜单交给主厨？"
      :content="`已选 ${selectedIds.length} 道菜，主厨确认后开始采购。`"
      confirm-text="交给主厨"
      @confirm="confirmCheckout"
      @cancel="checkoutVisible = false"
    />

    <ConfirmDialog
      :visible="qtyEditor.visible"
      title="修改数量"
      :content="`当前：${qtyEditor.item?.dishName || ''}`"
      tone="primary"
      confirm-text="保存"
      cancel-text="取消"
      @confirm="confirmQtyEditor"
      @cancel="qtyEditor.visible = false"
    >
      <input
        class="qty-input"
        type="number"
        v-model.number="qtyEditor.qty"
        :maxlength="3"
      />
    </ConfirmDialog>

    <ConfirmDialog
      :visible="noChefVisible"
      title="还没人认领主厨"
      content="下单前需要家庭中有人认领主厨哦，去认领一位？"
      tone="chef"
      icon="chef-hat"
      confirm-text="去认领"
      cancel-text="稍后"
      @confirm="goClaimChef"
      @cancel="noChefVisible = false"
    />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { getMyCart, removeCartItem, updateCartQuantity, checkoutOrders, getToken, getUserInfo } from '../../utils/request.js'
import { ensureSubscribe, warmPushConfig } from '../../utils/subscribe.js'
import Icon from '../../components/Icon.vue'
import TapBurst from '../../components/TapBurst.vue'
import SkeletonBlock from '../../components/SkeletonBlock.vue'
import ConfirmDialog from '../../components/ConfirmDialog.vue'

const cartItems = ref([])
const selectedIds = ref([])
const loading = ref(false)
const error = ref(false)
const checking = ref(false)
const addedIds = ref(new Set())

// === 5s 缓存：tabBar 页面每次切到都触发 onShow，原代码会重拉。
// 切别处 5s 内回来（看数量、看价格）直接复用旧数据，不闪 loading。
// 任何加减删/结算后会 invalidateCache()，下次进购物车强制刷新。
const CART_CACHE_TTL_MS = 5000
const lastFetchedAt = ref(0)        // 上次成功拉到的 Date.now()

// 自定义弹窗状态
const checkoutVisible = ref(false)
const noChefVisible = ref(false)
const qtyEditor = ref({ visible: false, item: null, qty: 1 })

const totalAmount = computed(() =>
  cartItems.value.reduce((s, i) => s + parseFloat(i.price || 0) * (i.quantity || 1), 0).toFixed(2)
)

const selectedTotal = computed(() => {
  const ids = new Set(selectedIds.value)
  return cartItems.value
    .filter(i => ids.has(i.id))
    .reduce((s, i) => s + parseFloat(i.price || 0) * (i.quantity || 1), 0)
    .toFixed(2)
})

const allSelected = computed(() =>
  cartItems.value.length > 0 && selectedIds.value.length === cartItems.value.length
)

const invalidateCartCache = () => { lastFetchedAt.value = 0 }

const loadCart = async (force = false) => {
  if (loading.value) return
  // 没 token 直接返回（避免 401 触发自动重登录链路导致渲染层错误）
  if (!getToken()) {
    cartItems.value = []
    selectedIds.value = []
    lastFetchedAt.value = 0
    loading.value = false
    error.value = false
    return
  }
  // === 缓存命中：5s 内不重拉（但仍通知页面角标刷新） ===
  const now = Date.now()
  if (!force && now - lastFetchedAt.value < CART_CACHE_TTL_MS) {
    uni.$emit('cart:changed')
    return
  }
  loading.value = true
  error.value = false
  try {
    const items = await getMyCart()
    cartItems.value = items || []
    selectedIds.value = cartItems.value.map(i => i.id)
    lastFetchedAt.value = Date.now()
  } catch (e) {
    error.value = true
    cartItems.value = []
    selectedIds.value = []
  } finally {
    loading.value = false
    uni.$emit('cart:changed')
  }
}

const toggleItem = (id) => {
  const idx = selectedIds.value.indexOf(id)
  if (idx === -1) selectedIds.value.push(id)
  else selectedIds.value.splice(idx, 1)
}

const toggleAll = () => {
  if (allSelected.value) selectedIds.value = []
  else selectedIds.value = cartItems.value.map(i => i.id)
}

const openQtyEditor = (item) => {
  qtyEditor.value = { visible: true, item, qty: item.quantity }
}

const confirmQtyEditor = async () => {
  const { item, qty } = qtyEditor.value
  if (!item) return
  const n = parseInt(qty)
  if (isNaN(n) || n <= 0) {
    await removeItem(item.id)
  } else {
    await updateQty(item.id, n)
  }
  qtyEditor.value.visible = false
}

const changeQty = async (item, delta) => {
  const newQty = item.quantity + delta
  if (newQty <= 0) {
    await removeItem(item.id)
  } else {
    await updateQty(item.id, newQty)
    addedIds.value = new Set([...addedIds.value, item.id])
    setTimeout(() => {
      const next = new Set(addedIds.value); next.delete(item.id)
      addedIds.value = next
    }, 380)
  }
}

const updateQty = async (id, qty) => {
  try {
    await updateCartQuantity(id, qty)
    const item = cartItems.value.find(i => i.id === id)
    if (item) item.quantity = qty
    uni.$emit('cart:changed')
  } catch (e) {
    uni.showToast({ title: '修改失败', icon: 'none' })
  }
}

const removeItem = async (id) => {
  try {
    await removeCartItem(id)
    uni.showToast({ title: '已删除', icon: 'none' })
    selectedIds.value = selectedIds.value.filter(x => x !== id)
    await loadCart()
  } catch (e) {}
}

const openCheckout = () => {
  if (selectedIds.value.length === 0) {
    uni.showToast({ title: '请先选择菜品', icon: 'none' })
    return
  }
  checkoutVisible.value = true
}

const confirmCheckout = async () => {
  checkoutVisible.value = false
  checking.value = true
  // 顺带要一次订阅授权：下单后主厨确认/驳回时，微信「服务通知」能提醒到我自己。
  // 不 await——授权面板是否弹出、用户是否允许，都不该影响下单本身。
  ensureSubscribe('MEMBER')
  try {
    const totalQty = cartItems.value
      .filter(i => selectedIds.value.includes(i.id))
      .reduce((s, i) => s + (i.quantity || 1), 0)
    await checkoutOrders(selectedIds.value)
    uni.showToast({ title: `心意已收，共 ${totalQty} 道菜`, icon: 'success' })
    selectedIds.value = []
    await loadCart()
    uni.$emit('cart:changed')
  } catch (err) {
    const msg = (err && err.message) || ''
    if (msg.includes('主厨')) noChefVisible.value = true
  } finally {
    checking.value = false
  }
}

const goClaimChef = () => {
  noChefVisible.value = false
  // 修复：之前没传 familyId，导致 detail.vue 拿到 NaN→"家庭不存在"
  const info = getUserInfo()
  const fid = info?.activeFamilyId || info?.families?.find(f => f.active)?.familyId
  if (!fid) {
    uni.showToast({ title: '请先选择家庭', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/family/detail?id=${fid}` })
}

// 重试按钮：强制绕缓存（因为用户既然点了重试，一定是看到错误页了）
const onRetry = () => loadCart(true)

const goHome = () => uni.switchTab({ url: '/pages/index/index' })

onShow(() => {
  // 5s TTL：tabBar 反复切换时复用旧数据，不闪 loading
  loadCart()
  warmPushConfig()
})
onPullDownRefresh(async () => { await loadCart(true); uni.stopPullDownRefresh() })
</script>

<style scoped>
/* ============= 甜美少女粉·购物车 ============= */
.cart-page { padding-bottom: calc(180rpx + env(safe-area-inset-bottom)); }

.header {
  padding: 2% 5% 5%;
  color: var(--c-text);
}
.sub {
  font-size: var(--t-sm); font-weight: 300;
  color: var(--c-text-2);
  letter-spacing: 1rpx;
}

.loading-mask {
  padding: 11% 0;
  display: flex; flex-direction: column; align-items: center; gap: 3%;
}

.empty-state {
  margin: 4% 4% 0;
  text-align: center;
  padding: 14% 6%;
  border-radius: 32rpx;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  box-shadow: var(--glow-soft);
}
.empty-icon { display: block; margin: 0 auto 3%; }
.empty-title { display: block; font-size: var(--t-lg); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.empty-hint  { display: block; font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); margin-top: 2%; }
.go-btn { margin-top: 5%; padding: 3% 8%; font-size: var(--t-md); display: inline-block; font-weight: 400 !important; letter-spacing: 2rpx !important; }

.cart-list { padding: 4% 4%; display: flex; flex-direction: column; gap: 24rpx; }
.cart-item {
  border-radius: 32rpx;
  padding: 4% 5%;
  display: flex; align-items: center; gap: 3%;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  box-shadow: var(--glow-soft);
}
.item-emoji-wrap {
  width: 84rpx; height: 84rpx;
  background: rgba(255,255,255,0.7);
  border: 2rpx solid rgba(255,255,255,0.55);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}
.item-body { flex: 1; min-width: 0; }
.item-name { font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose); display: block; letter-spacing: 1rpx; }
.item-meta { display: flex; gap: 2%; margin-top: 1%; flex-wrap: wrap; }
.tag { font-size: 20rpx; padding: 4rpx 12rpx; border-radius: 999rpx; white-space: nowrap; font-weight: 400; }
.spice-tag { display: inline-flex; align-items: center; }
.spice-tag::before {
  content: ''; display: inline-block; width: 8rpx; height: 8rpx;
  background: currentColor; border-radius: 50%;
  margin-right: 1%;
}
.item-hint { font-size: 62.5%; font-weight: 300; color: var(--c-text-2); display: block; margin-top: 1%; }

/* 数量加减 */
.qty-wrap {
  display: flex; align-items: center; gap: 0;
  background: rgba(255,123,148,0.12);
  border: 1rpx solid rgba(255,123,148,0.2);
  border-radius: 999rpx; overflow: hidden;
  flex-shrink: 0;
  backdrop-filter: blur(8rpx);
  -webkit-backdrop-filter: blur(8rpx);
}
.qty-btn {
  width: 52rpx; height: 52rpx;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255,255,255,0.5);
}
.qty-num {
  min-width: 56rpx; text-align: center;
  font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose);
  padding: 0 8rpx;
}
.item-right { display: flex; flex-direction: column; align-items: flex-end; flex-shrink: 0; }
.item-price { font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }

/* 复选框 */
.check-wrap { display: flex; align-items: center; }
.checkbox {
  width: 40rpx; height: 40rpx; border-radius: 50%;
  border: 2rpx solid rgba(122,74,90,.3);
  background: rgba(255,255,255,0.7);
  display: flex; align-items: center; justify-content: center;
  transition: background .18s ease;
  backdrop-filter: blur(8rpx);
  -webkit-backdrop-filter: blur(8rpx);
}
.checkbox.checked {
  background: var(--g-primary);
  border-color: transparent;
  box-shadow: var(--glow-pink);
}

/* 底部 */
.bottom-bar {
  position: fixed; bottom: 0; left: 0; right: 0;
  padding: 2% 4%;
  padding-bottom: calc(2% + constant(safe-area-inset-bottom));
  padding-bottom: calc(2% + env(safe-area-inset-bottom));
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(30rpx);
  -webkit-backdrop-filter: blur(30rpx);
  border-top: 1rpx solid rgba(255,255,255,0.5);
  box-shadow: 0 -8rpx 32rpx rgba(255,194,207,.18);
  display: flex; align-items: center; gap: 4%; z-index: 100;
  box-sizing: border-box;
}
.bottom-bar .check-wrap { flex-shrink: 0; white-space: nowrap; }
.checkout-burst { display: block; flex-shrink: 0; min-width: 220rpx; }
.all-text { font-size: var(--t-sm); font-weight: 400; color: var(--c-deep-rose); margin-left: 2%; white-space: nowrap; }
.total-info { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.total-amount { font-size: var(--t-lg); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.total-count { font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2); }
.checkout-btn {
  width: 100%;
  padding: 4% 8%;
  font-size: var(--t-lg); font-weight: 400 !important;
  letter-spacing: 2rpx !important;
  border-radius: 999rpx !important;
  box-shadow: var(--glow-pink);
}

.qty-input {
  width: 100%;
  padding: 5%;
  text-align: center;
  font-size: var(--t-xl);
  font-weight: 500;
  border: 2rpx solid rgba(122,74,90,.25);
  border-radius: 16rpx;
  margin-top: 2%;
  color: var(--c-deep-rose);
  box-sizing: border-box;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}
</style>