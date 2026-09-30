<template>
  <view class="mine-page page-bg-ethereal">
    <!-- 顶部用户信息 -->
    <view class="profile-header">
      <view class="avatar-ring anim-breath">
        <image
          :src="avatarSrc(userInfo.avatarUrl)"
          class="avatar-img"
          mode="aspectFit"
        />
        <view class="avatar-edit-hint">
          <Icon name="edit" size="20rpx" tone="white" />
        </view>
      </view>
      <view class="profile-info">
        <view class="profile-name-row">
          <text class="profile-name">{{ userInfo.nickname || '用户' }}</text>
          <view class="edit-name-btn pressable" @tap="openEditName">
            <Icon name="edit" size="22rpx" tone="white" />
            <text>编辑</text>
          </view>
        </view>
        <view class="profile-badge" :class="activeFamily && activeFamily.role === 'CHEF' ? 'chef-badge' : 'user-badge'">
          <Icon
            :name="activeFamily && activeFamily.role === 'CHEF' ? 'chef-hat' : 'family'"
            size="22rpx"
            tone="primary"
          />
          <text>{{ activeFamily && activeFamily.role === 'CHEF' ? '掌勺人' : '家庭成员' }}</text>
        </view>
      </view>
    </view>

    <!-- 我的家：点卡片中间进家庭管理页（在该页点选切换家庭） -->
    <view class="family-card card pressable" v-if="activeFamily" @tap="goFamily">
      <view class="fc-main">
        <view class="fc-emoji-wrap">
          <Icon
            :name="activeFamily.isOwner ? 'crown' : 'home'"
            size="48rpx"
            tone="primary"
          />
        </view>
        <view class="fc-info">
          <text class="fc-name">{{ activeFamily.name }}</text>
          <text class="fc-code">码：{{ activeFamily.code }} · {{ roleText(activeFamily.role) }}</text>
        </view>
      </view>
      <view class="fc-go">
        <Icon name="right" size="24rpx" tone="primary" />
      </view>
    </view>

    <!-- 无家庭 -->
    <view v-else class="family-card empty-card card pressable" @tap="goFamily">
      <Icon name="family" size="48rpx" tone="primary" />
      <text class="empty-text">还没有家庭，去创建或加入</text>
      <Icon name="right" size="24rpx" tone="primary" />
    </view>

    <!-- 主厨状态：纯信息卡片 -->
    <view class="chef-line card" v-if="activeFamily">
      <view class="cl-icon-wrap">
        <Icon name="chef-hat" size="32rpx" tone="chef" />
      </view>
      <view class="cl-info">
        <text class="cl-label">当前主厨</text>
        <text class="cl-value">{{ chefStatus.chefNickname || '无人认领' }}</text>
      </view>
    </view>

    <!-- 功能列表 -->
    <view class="menu-card card">
      <view class="menu-item pressable" @tap="goHistory">
        <Icon name="history" size="40rpx" tone="primary" />
        <text class="menu-name">家庭订单</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item pressable" @tap="goShoppingList">
        <Icon name="cart" size="40rpx" tone="primary" />
        <text class="menu-name">买菜清单</text>
        <text class="menu-arrow">›</text>
      </view>
      <template v-if="canManageMenu">
        <view class="menu-item pressable" @tap="goEditDishes">
          <Icon name="spoon" size="40rpx" tone="primary" />
          <text class="menu-name">编辑菜单</text>
          <text class="menu-arrow">›</text>
        </view>
        <view class="menu-item pressable" @tap="goEditIngredients">
          <Icon name="tomato" size="40rpx" tone="primary" />
          <text class="menu-name">编辑配菜</text>
          <text class="menu-arrow">›</text>
        </view>
      </template>
      <template v-if="canConfirm">
        <view class="menu-item pressable" @tap="goConfirmOrders">
          <Icon name="check" size="40rpx" tone="primary" />
          <text class="menu-name">订单确认</text>
          <view v-if="pendingCount > 0" class="menu-badge anim-heartbeat">
            <text>{{ pendingCount }} 个未确认</text>
          </view>
          <text class="menu-arrow">›</text>
        </view>
      </template>
    </view>

    <!-- 通知设置：微信一次性订阅，一次允许只能收一条，所以要能随时补授权 -->
    <view class="menu-card card">
      <view class="menu-item pressable" @tap="openNotify">
        <Icon name="bell" size="40rpx" tone="primary" />
        <text class="menu-name">{{ notifyLabel }}</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <!-- 退出登录 -->
    <view class="logout-section">
      <button class="logout-btn btn-ghost pressable" @tap="openLogoutConfirm">
        <Icon name="logout" size="32rpx" tone="primary" />
        <text>退出登录</text>
      </button>
    </view>

    <!-- 编辑昵称弹层 -->
    <view v-if="editVisible" class="edit-mask" @tap="editVisible = false">
      <view class="edit-card anim-bounce-in" @tap.stop>
        <view class="edit-title">
          <Icon name="edit" size="32rpx" tone="primary" />
          <text>修改昵称</text>
        </view>
        <input
          class="edit-input"
          v-model="editName"
          placeholder="请输入新的昵称"
          placeholder-class="edit-placeholder"
          maxlength="20"
          focus
          confirm-type="done"
          @confirm="saveName"
        />
        <view class="edit-tip">昵称会展示给家庭成员（最多 20 字）</view>
        <view class="edit-actions">
          <button class="btn-ghost edit-btn" @tap="editVisible = false">取消</button>
          <button class="btn-primary edit-btn" :loading="editSaving" :disabled="editSaving" @tap="saveName">保存</button>
        </view>
      </view>
    </view>

    <!-- 退出登录确认 -->
    <ConfirmDialog
      :visible="logoutVisible"
      title="确认退出？"
      content="退出后需要重新登录"
      tone="danger"
      icon="logout"
      confirm-text="退出登录"
      @confirm="confirmLogout"
      @cancel="logoutVisible = false"
    />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onUnload } from '@dcloudio/uni-app'
import {
  getMyFamilies, getFamilyChefStatus, getUserInfo, clearAuth,
  getMyOrders, updateNickname, avatarSrc
} from '../../utils/request.js'
import { ensureSubscribeBulk, warmPushConfig, currentRole, NOTIFY_TARGET } from '../../utils/subscribe.js'
import Icon from '../../components/Icon.vue'
import ConfirmDialog from '../../components/ConfirmDialog.vue'


const loading = ref(false)
const userInfo = ref(getUserInfo())
const families = ref([])
const activeFamily = ref(null)
const chefStatus = ref({ hasChef: false, chefNickname: '' })
const pendingCount = ref(0)
// 页面已卸载：阻止 onShow 触发的异步任务在 await 之后再次发起路由跳转
let unmounted = false

const editVisible = ref(false)
const editName = ref('')
const editSaving = ref(false)
const logoutVisible = ref(false)

const roleText = (r) => ({ OWNER: '创建者', CHEF: '主厨', MEMBER: '成员' })[r] || r

const openEditName = () => {
  editName.value = (userInfo.value && userInfo.value.nickname) || ''
  editVisible.value = true
}

const saveName = async () => {
  const name = (editName.value || '').trim()
  if (!name) {
    uni.showToast({ title: '昵称不能为空', icon: 'none' })
    return
  }
  editSaving.value = true
  try {
    const data = await updateNickname(name)
    if (data && data.nickname) {
      userInfo.value = getUserInfo()
      editVisible.value = false
      uni.showToast({ title: '昵称已更新', icon: 'success' })
    }
  } catch (e) {} finally {
    editSaving.value = false
  }
}

// 只对主厨(CHEF)开放；家庭创建者(OWNER)只能查看，不能编辑菜单/配菜、确认订单
const canManageMenu = computed(() =>
  activeFamily.value && activeFamily.value.role === 'CHEF')
const canConfirm = computed(() => canManageMenu.value)
const isChef = computed(() => activeFamily.value && activeFamily.value.role === 'CHEF')

/** 通知入口文案：主厨收「点菜提醒」，成员收「出餐提醒」 */
const notifyLabel = computed(() => (isChef.value ? '点菜提醒' : '出餐提醒'))

/**
 * 点「通知设置」→ 补微信订阅授权额度。
 *
 * 微信一次性订阅：点一次「允许」= 服务端只能发一条，所以这里一次要 {@link NOTIFY_TARGET} 条：
 * 用户若勾了「总是保持以上选择，不再询问」，后续几次会静默累加，不会再弹面板；
 * 没勾就只拿到 1 条，绝不连着弹第二次面板骚扰用户（见 ensureSubscribeBulk）。
 */
const openNotify = () => {
  const role = isChef.value ? 'CHEF' : 'MEMBER'
  // ⚠️ 这里不能先 await warmPushConfig：微信要求 requestSubscribeMessage 落在用户点击的
  //    同步调用链里，任何 await（哪怕只是微任务）都会让手势失效、面板静默不弹。
  //    onShow 已经预热过配置，ensureSubscribeBulk 内部同步读缓存。
  warmPushConfig(role)
  ensureSubscribeBulk(role, NOTIFY_TARGET).then((n) => {
    if (n <= 0) {
      // 配置没预热好（弱网首进）/ 用户拒绝 / 被封禁
      uni.showToast({ title: '未开启，可稍后再试', icon: 'none' })
      return
    }
    uni.showToast({
      title: n > 1 ? `已开启，还能收 ${n} 条` : '已开启，收到通知即生效',
      icon: 'none'
    })
  })
}

const loadData = async () => {
  if (loading.value) return
  loading.value = true
  userInfo.value = getUserInfo()
  try {
    const list = await getMyFamilies() || []
    if (unmounted) return
    families.value = list
    activeFamily.value = list.find(f => f.active) || list[0] || null
    // 没有家庭也留在本页：页面上本来就有「还没有家庭，去创建或加入」的卡片入口
    const tasks = activeFamily.value
      ? [getFamilyChefStatus(activeFamily.value.familyId).catch(() => ({}))]
      : []
    if (canConfirm.value) {
      tasks.push(
        getMyOrders().then(orders => {
          pendingCount.value = (orders || []).filter(o => o.status === 0).length
        }).catch(() => { pendingCount.value = 0 })
      )
    }
    const [chef] = await Promise.all(tasks)
    if (unmounted) return
    chefStatus.value = chef || {}
  } catch (e) {} finally {
    loading.value = false
  }
}

const goFamily = () => uni.navigateTo({ url: '/pages/family/family' })
const openLogoutConfirm = () => { logoutVisible.value = true }
const confirmLogout = () => {
  logoutVisible.value = false
  clearAuth()
  // 设置「用户主动退出」标记：欢迎页不会再自动进入，401 也不会静默重登
  try { uni.setStorageSync('logout_by_user', 1) } catch (e) {}
  uni.reLaunch({ url: '/pages/index/index' })
}
const onProfileSaved = (info) => {
  // 更新本地头像/昵称显示
  userInfo.value = getUserInfo()
}
const goHistory = () => uni.navigateTo({ url: '/pages/order/order' })
const goShoppingList = () => uni.navigateTo({ url: '/pages/shopping-list/shopping-list' })
const goEditDishes = () => uni.navigateTo({ url: '/pages/edit-dishes/edit-dishes' })
const goEditIngredients = () => uni.navigateTo({ url: '/pages/edit-ingredients/edit-ingredients' })
const goConfirmOrders = () => uni.navigateTo({ url: '/pages/confirm-orders/confirm-orders' })

onShow(() => { loadData(); warmPushConfig(currentRole()) })
onUnload(() => { unmounted = true })
uni.$on('family:changed', loadData)
</script>

<style scoped>
/* ============= 甜美少女粉·我的 ============= */
.mine-page { padding-bottom: 8%; }

/* 顶部用户信息：浮在渐变上 */
.profile-header {
  padding: 6% 5% 8%;
  display: flex; align-items: center; gap: 4%;
  color: var(--c-text);
}
.avatar-ring {
  width: 132rpx; height: 132rpx;
  background: rgba(255,255,255,0.85);
  border-radius: 50%;
  border: 4rpx solid rgba(255,255,255,0.7);
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
  box-shadow: var(--glow-pink);
  backdrop-filter: blur(16rpx);
  -webkit-backdrop-filter: blur(16rpx);
}
.avatar-img {
  width: 100%; height: 100%;
  border-radius: 50%;
}
.avatar-edit-hint {
  position: absolute;
  right: -2rpx; bottom: -2rpx;
  width: 36rpx; height: 36rpx;
  background: var(--g-primary);
  border: 2rpx solid white;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  box-shadow: var(--glow-soft);
}
.profile-info { display: flex; flex-direction: column; gap: 2%; flex: 1; min-width: 0; }
.profile-name-row { display: flex; align-items: center; gap: 3%; }
.profile-name {
  font-size: var(--t-xl); font-weight: 300;
  color: var(--c-text);
  letter-spacing: 2rpx;
  text-shadow: 0 4rpx 16rpx rgba(255,123,148,.22);
}
.edit-name-btn {
  display: inline-flex; align-items: center; gap: 1%;
  font-size: 22rpx; font-weight: 300;
  color: var(--c-deep-rose);
  background: rgba(255,255,255,.55);
  padding: 4rpx 14rpx; border-radius: 999rpx;
  border: 1rpx solid rgba(255,255,255,.55);
  white-space: nowrap;
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}
.profile-badge {
  display: inline-flex; align-items: center; gap: 1%;
  font-size: 22rpx;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  font-weight: 400;
  align-self: flex-start;
  white-space: nowrap;
}
.chef-badge {
  background: rgba(255,255,255,.85);
  color: var(--c-chef);
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}
.user-badge {
  background: rgba(255,255,255,.6);
  color: var(--c-deep-rose);
  border: 1rpx solid rgba(255,255,255,.55);
}

/* 我的家卡片 */
.family-card {
  margin: 0 4%;
  padding: 4% 5%;
  display: flex; align-items: center; gap: 2%;
  border-radius: 32rpx;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  box-shadow: var(--glow-soft);
}
.family-card.empty-card { justify-content: space-between; padding: 5% 4%; }
.empty-text {
  flex: 1; font-size: var(--t-md); font-weight: 400;
  color: var(--c-deep-rose); margin-left: 3%;
  letter-spacing: 1rpx;
}

.fc-main { flex: 1; min-width: 0; display: flex; align-items: center; gap: 3%; }
.fc-emoji-wrap {
  width: 80rpx; height: 80rpx;
  background: rgba(255,255,255,0.7);
  border: 2rpx solid rgba(255,255,255,0.6);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}
.fc-info { display: flex; flex-direction: column; gap: 1%; flex: 1; min-width: 0; }
.fc-name { font-size: var(--t-lg); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.fc-code { font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2); font-family: monospace; letter-spacing: 1rpx; }
.fc-go { flex-shrink: 0; display: flex; align-items: center; }

/* 主厨状态卡 */
.chef-line {
  display: flex; align-items: center; gap: 3%;
  padding: 4% 5%;
  margin: 4% 4% 0;
  border-radius: 32rpx;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  box-shadow: var(--glow-blue);
}
.cl-icon-wrap {
  width: 72rpx; height: 72rpx;
  border-radius: 50%;
  background: var(--c-chef-light);
  border: 2rpx solid rgba(255,123,148,.25);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.cl-info { display: flex; flex-direction: column; gap: 0.5%; flex: 1; min-width: 0; }
.cl-label { font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); }
.cl-value { font-size: var(--t-md); font-weight: 500; color: var(--c-chef); letter-spacing: 1rpx; }
.cl-hint {
  font-size: var(--t-xs); font-weight: 300;
  color: var(--c-text-2);
  flex-shrink: 0;
  max-width: 50%;
  text-align: right;
}

/* 功能菜单卡 */
.menu-card {
  margin: 4% 4% 0;
  padding: 2% 0;
  border-radius: 32rpx;
  overflow: hidden;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  box-shadow: var(--glow-soft);
}
.menu-item {
  display: flex; align-items: center; gap: 3%;
  padding: 4% 5%;
  border-bottom: 1rpx solid rgba(122,74,90,.12);
}
.menu-item:last-child { border-bottom: none; }
.menu-name { flex: 1; font-size: var(--t-md); font-weight: 400; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.menu-arrow { font-size: var(--t-xl); font-weight: 300; color: var(--c-text-2); }
.menu-hint {
  font-size: var(--t-sm);
  font-weight: 300;
  color: var(--c-text-2);
  white-space: nowrap;
  flex-shrink: 0;
}

/* 红点 badge：保留品牌红作为强调色 */
.menu-badge {
  background: var(--c-danger); color: white;
  font-size: 20rpx; padding: 4rpx 14rpx;
  border-radius: 999rpx; font-weight: 400;
  margin-right: 1.5%;
  flex-shrink: 0;
  white-space: nowrap;
  box-shadow: 0 4rpx 12rpx rgba(229,72,77,.3);
}

.logout-section {
  padding: 8% 5% 10%;
  display: flex;
  justify-content: center;
}
.logout-btn {
  width: 36%;
  min-height: 88rpx;
  font-size: var(--t-md);
  padding: 4% 0;
  display: flex; align-items: center; justify-content: center; gap: 2%;
  border-radius: 999rpx !important;
  font-weight: 400 !important;
  letter-spacing: 2rpx !important;
}

/* 昵称编辑弹层 */
.edit-mask {
  position: fixed; inset: 0; z-index: 9999;
  background: rgba(122,74,90,0.45);  /* 半透深紫，比纯黑更柔和 */
  display: flex; align-items: center; justify-content: center;
  padding: 0 8%;
}
.edit-card {
  width: 100%; max-width: 600rpx;
  border-radius: 32rpx;
  padding: 6% 5% 5%;
  background: rgba(255,255,255,0.92);
  backdrop-filter: blur(40rpx);
  -webkit-backdrop-filter: blur(40rpx);
  border: 1rpx solid rgba(255,255,255,0.7);
  box-shadow: var(--glow-deep);
}
.edit-title {
  display: flex; align-items: center; justify-content: center; gap: 2%;
  font-size: var(--t-lg); font-weight: 500; color: var(--c-deep-rose);
  letter-spacing: 1rpx;
  margin-bottom: 5%;
}
.edit-input {
  background: rgba(255,255,255,0.6);
  border: 2rpx solid rgba(122,74,90,.25);
  border-radius: 16rpx;
  padding: 3% 4%;
  font-size: var(--t-md); font-weight: 400;
  color: var(--c-deep-rose);
}
.edit-placeholder { color: var(--c-text-3); }
.edit-tip { font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2); margin-top: 2.5%; }
.edit-actions { display: flex; gap: 3%; margin-top: 5%; }
.edit-btn {
  flex: 1; border-radius: var(--r-pill) !important;
  font-size: var(--t-md);
  font-weight: 400 !important;
  letter-spacing: 2rpx !important;
  padding: 18rpx 0;
}
</style>