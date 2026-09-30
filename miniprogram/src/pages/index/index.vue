<template>
  <view class="index-page page-bg-ethereal">
    <!-- ===== 顶部深粉渐变层（长列表适配的关键）=====
         ⚠️ 绝不把渐变铺满 100vh：铺满会让页面底部的浅色区也变粉，
            白卡片和深色文字一起糊掉。
         这里是 fixed + 400rpx + mask 向下衰减到透明：
            · 列表怎么滚，背景都不动，不会随滚动长度在底部变形；
            · 底部自然过渡到页面兜底的极浅薄荷 #DAFFFB / #FDF8F9。
         内容层靠 .hero-layer 的 z-index:1 浮在上面。 -->
    <view class="hero-bg"></view>

    <!-- CSS 四角星光：只放在顶部深粉段（白星在深粉上才看得见；
         浅色段要用 .star--deep 的珊瑚粉星） -->
    <view class="stars stars--hero">
      <view class="star star--spark" style="left:12%;top:6%;--d:0s"></view>
      <view class="star star--spark star--sm" style="left:78%;top:9%;--d:.8s"></view>
      <view class="star" style="left:33%;top:14%;--d:1.6s"></view>
      <view class="star star--spark star--lg" style="left:62%;top:20%;--d:2.4s"></view>
      <view class="star star--spark" style="left:22%;top:26%;--d:1.1s"></view>
      <view class="star" style="left:88%;top:30%;--d:.4s"></view>
    </view>

    <view class="hero-layer">
    <!-- 顶部家庭 Banner：纯展示（切换家庭已收拢到 我的 → 家庭管理页） -->
    <view class="chef-banner">
      <view class="banner-left">
        <view class="banner-emoji-wrap">
          <Icon
            :name="chefStatus.isMe ? 'chef-hat' : (activeFamily && activeFamily.isOwner ? 'crown' : 'home')"
            size="56rpx"
            tone="primary"
            class="anim-breath"
          />
        </view>
        <view class="banner-info">
          <text class="banner-title">{{ activeFamily ? activeFamily.name : '勺意 · 家庭厨房' }}</text>
          <text class="banner-sub" v-if="chefStatus.hasChef">{{ chefStatus.chefNickname || '主厨' }} 正在准备美食</text>
          <text class="banner-sub" v-else>今天想吃点什么？</text>
        </view>
      </view>
    </view>

    <!-- 双入口大按钮 -->
    <view class="entries">
      <TapBurst :icons="['spoon','heart','tomato']" class="entry-burst">
        <view class="entry-btn entry-menu pressable" :class="{ disabled: !isLoggedIn }" @tap="goExistingMenu">
          <view class="entry-icon-wrap">
            <Icon name="spoon" size="64rpx" tone="white" />
          </view>
          <view class="entry-info">
            <view class="entry-title">已有菜单点餐</view>
            <view class="entry-sub">{{ activeFamily ? `从 ${dishCount} 道固定菜里选菜` : '需先创建或加入家庭' }}</view>
          </view>
          <view v-if="!isLoggedIn" class="entry-mask">
            <text class="entry-lock">🔒 请先登录</text>
          </view>
        </view>
      </TapBurst>

      <TapBurst :icons="['flower','sparkle','heart-egg']" class="entry-burst">
        <view class="entry-btn entry-custom pressable" :class="{ disabled: !isLoggedIn }" @tap="goCustomDish">
          <view class="entry-icon-wrap">
            <Icon name="heart-egg" size="64rpx" tone="dark" />
          </view>
          <view class="entry-info">
            <view class="entry-title">自定义菜品</view>
            <view class="entry-sub">{{ activeFamily ? `从 ${ingCount} 种配菜自由组合` : '需先创建或加入家庭' }}</view>
          </view>
          <view v-if="!isLoggedIn" class="entry-mask">
            <text class="entry-lock">🔒 请先登录</text>
          </view>
        </view>
      </TapBurst>
    </view>

    <!-- 未登录时的显式登录按钮（位于入口下方） -->
    <view v-if="!isLoggedIn" class="login-banner card anim-fade-up">
      <text class="login-banner-title">登录后即可点餐</text>
      <text class="login-banner-hint">首次登录会自动注册账号</text>
      <button class="login-banner-btn" :disabled="loggingIn" @tap="onTapLogin">
        {{ loggingIn ? '登录中…' : '立即登录' }}
      </button>
    </view>

    <!-- 今日概览 -->
    <view class="overview-card card" v-if="todayOrders.length > 0">
      <view class="overview-title">
        <Icon name="sparkle" size="28rpx" tone="gold" />
        <text>今日菜单</text>
      </view>
      <view class="overview-stats">
        <view class="stat-item">
          <text class="stat-num">{{ totalItems }}</text>
          <text class="stat-label">道菜</text>
        </view>
        <view class="stat-divider"></view>
        <view class="stat-item">
          <text class="stat-num">¥{{ todayTotal }}</text>
          <text class="stat-label">合计</text>
        </view>
        <view class="stat-divider"></view>
        <view class="stat-item">
          <text class="stat-num">{{ confirmedCount }}</text>
          <text class="stat-label">已确认</text>
        </view>
      </view>
    </view>

    <!-- 今日菜单（按订单） -->
    <view class="menu-list" v-if="todayOrders.length > 0">
      <view
        v-for="order in todayOrders"
        :key="order.orderId"
        class="order-group pressable"
        :class="`og-${statusClass(order.status)}`"
        @tap="openOrder(order.orderId)"
      >
        <view class="og-head">
          <view class="og-no">
            <Icon name="cart" size="32rpx" tone="primary" />
            <text>订单 #{{ order.orderId }}</text>
          </view>
          <view class="og-status" :class="'st-' + order.status">
            {{ statusText(order.status) }}
          </view>
        </view>
        <view class="og-meta">
          <Icon name="user" size="26rpx" tone="primary" />
          <text class="og-user">{{ order.userNickname }}</text>
          <text class="og-time">{{ fmtTime(order.createdAt) }}</text>
        </view>
        <!-- 只显示数量统计；每道菜的状态点进订单详情才能看到 -->
        <view class="og-stats">
          <text class="og-stat">{{ order.itemCount }} 道菜</text>
          <text class="og-stat ok">已确认 {{ order.confirmedCount }}</text>
          <text class="og-stat no">已驳回 {{ order.rejectedCount || 0 }}</text>
          <text class="og-stat wait">待确认 {{ pendingItemCount(order) }}</text>
        </view>
        <view class="og-foot">
          <text class="og-total">¥{{ fmtMoney(order.totalAmount) }}</text>
          <text class="og-arrow">查看详情 ›</text>
        </view>
      </view>
    </view>

    <!-- 首次加载骨架：避免冷启动时菜单区一片空白 -->
    <view v-else-if="firstLoading && isLoggedIn" class="menu-skeleton">
      <view class="card">
        <SkeletonBlock height="36rpx" width="40%" />
        <SkeletonBlock height="28rpx" width="70%" />
        <SkeletonBlock height="120rpx" />
      </view>
      <view class="card">
        <SkeletonBlock height="36rpx" width="45%" />
        <SkeletonBlock height="80rpx" />
      </view>
    </view>

    <view v-else class="empty-state card anim-fade-up">

        <view class="empty-illus"><Icon name="heart-egg" size="120rpx" tone="primary" class="empty-icon" /></view>
        <text class="empty-title">今晚还没人下过单</text>
      <text class="empty-hint">从上方选一种方式开始点餐吧</text>
    </view>

    <!-- 新用户首次登录：引导用微信昵称当名字
         ⚠️ 必须两侧都收口，缺一不可：
         1) 外层 v-if —— 不给该组件创建实例，输入框压根不进渲染树；
         2) 组件内 .psc-mask 默认 visibility:hidden，props.visible 再二次控制。
         只靠 :visible 时，冷启动 / tab 快速切换的时序里 props 初值可能被当作 true，
         组件内的 <input type="nickname"> 会跟着出现，微信就会带出昵称候选并要求聚焦。 -->
    <ProfileSetupCard v-if="nicknamePromptVisible" :visible="nicknamePromptVisible" @close="nicknamePromptVisible = false" @saved="nicknamePromptVisible = false" />
    </view><!-- /hero-layer -->
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onUnload } from '@dcloudio/uni-app'
import {
  getChefStatus, getTodayTotal, getTodayOrders,
  getDishes, getIngredients, getToken, getMyFamilies,
  forceLogin, onAuthLogin, getUserInfo, setUserInfo
} from '../../utils/request.js'
import Icon from '../../components/Icon.vue'
import TapBurst from '../../components/TapBurst.vue'
import SkeletonBlock from '../../components/SkeletonBlock.vue'
import ProfileSetupCard from '../../components/ProfileSetupCard.vue'


const chefStatus = ref({ hasChef: false, isMe: false, chefNickname: '' })
const activeFamily = ref(null)
const todayOrders = ref([])
const todayTotal = ref('0.00')
const loading = ref(false)
const firstLoading = ref(true)   // 首次加载：仅登录态首页需要骨架
const dishCount = ref(0)
const ingCount = ref(0)
const isLoggedIn = ref(false)
const loggingIn = ref(false)

// 新用户首次登录 → 弹「用微信昵称」引导（isNewUser 由后端登录响应带回）
const nicknamePromptVisible = ref(false)

// 页面已卸载：避免 onShow 触发的异步任务在 await 之后再次发起路由跳转
let unmounted = false
const totalItems = computed(() => todayOrders.value.reduce((s, o) => s + (o.itemCount || 0), 0))
const confirmedCount = computed(() => todayOrders.value.reduce((s, o) => s + (o.confirmedCount || 0), 0))

const statusText = (s) => {
  if (s === 1) return '已确认'
  if (s === -1) return '已撤销'
  return '待确认'
}
// 菜品级待确认数 = 总数 - 已确认 - 已驳回
const pendingItemCount = (o) =>
  Math.max(0, (o.itemCount || 0) - (o.confirmedCount || 0) - (o.rejectedCount || 0))
const statusClass = (s) => {
  if (s === 1) return 'confirmed'
  if (s === -1) return 'cancelled'
  return 'pending'
}
const fmtMoney = (n) => (parseFloat(n || 0)).toFixed(2)
const fmtTime = (s) => {
  if (!s) return ''
  const d = new Date(s)
  if (isNaN(d)) return s
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getHours()}:${p(d.getMinutes())}`
}
const openOrder = (id) => uni.navigateTo({ url: `/pages/order/detail?id=${id}` })

const loadAll = async () => {
  if (!getToken()) return
  if (loading.value) return
  loading.value = true
  try {
    const [status, total, orders, dishes, ings, fams] = await Promise.all([
      getChefStatus().catch(() => ({})),
      getTodayTotal().catch(() => 0),
      getTodayOrders().catch(() => []),
      getDishes().catch(() => []),
      getIngredients().catch(() => []),
      getMyFamilies().catch(() => []),
    ])
    chefStatus.value = status || {}
    todayTotal.value = parseFloat(total || 0).toFixed(2)
    todayOrders.value = orders || []
    dishCount.value = (dishes || []).length
    ingCount.value = (ings || []).length
    if (unmounted) return
    activeFamily.value = (fams || []).find(f => f.active) || (fams || [])[0] || null
    // 没有家庭也留在主画面：只提示、不跳走，点菜时再由入口引导去创建/加入
    // 通知 App 更新 tabBar 角标
    uni.$emit('cart:changed')
  } finally {
    loading.value = false
    firstLoading.value = false
  }
}

const goExistingMenu = () => guardOrder(() => uni.navigateTo({ url: '/pages/category/category' }))
const goCustomDish   = () => guardOrder(() => uni.navigateTo({ url: '/pages/custom/custom' }))

/**
 * 点菜入口守卫：
 *   未登录 → 提示并触发登录
 *   登录了但还没有家庭 → 提示先创建/加入家庭（家人才能看到你点的菜）
 */
function guardOrder(action) {
  if (!isLoggedIn.value) {
    uni.showToast({ title: '请先登录再点餐', icon: 'none' })
    onTapLogin()
    return false
  }
  if (!activeFamily.value) {
    uni.showModal({
      title: '还差一个家',
      content: '点菜前需要先创建或加入一个家庭，这样家人才能看到你点的菜。',
      confirmText: '去创建/加入',
      cancelText: '先逛逛',
      success: (r) => {
        if (r.confirm) uni.navigateTo({ url: '/pages/family/family' })
      },
    })
    return false
  }
  action()
  return true
}

async function onTapLogin() {
  if (loggingIn.value) return
  loggingIn.value = true
  try {
    const { promise } = forceLogin({ showLoading: true })
    const ok = await promise
    if (ok) {
      // forceLogin 成功后通过 onAuthLogin 监听器会刷新 isLoggedIn
    } else {
      uni.showToast({ title: '网络异常，请稍后再试', icon: 'none' })
    }
  } finally {
    loggingIn.value = false
  }
}

// 监听登录成功事件（欢迎页点「立即登录」/ 本页登录入口 都会触发）
uni.$on('auth:login', () => {
  isLoggedIn.value = !!getToken()
  if (isLoggedIn.value) loadAll()
})

onShow(() => {
  isLoggedIn.value = !!getToken()
  syncNicknamePrompt()
  loadAll()
})

/**
 * 决定是否弹「用微信昵称当名字」引导。
 *
 * ⚠️ 关键：后端每次登录都会返回 isNewUser（true=本次请求新建了用户行），
 *    它一旦被写进本地 userInfo 就永远留在 storage 里 —— 老用户重登也会是 true。
 *    因此**不能**只凭 isNewUser 判断，必须同时确认「昵称还是系统默认值」。
 *    系统默认昵称格式：用户 + openId 后 6 位（见后端 wxLoginAs）。
 *    否则每次冷启动都会误判成新用户，弹出昵称输入框并唤起微信昵称候选。
 */
function syncNicknamePrompt() {
  if (!isLoggedIn.value) return
  if (nicknamePromptVisible.value) return
  const uInfo = getUserInfo() || {}
  // 本地被打标记说「已引导过」时，顺手修一次 storage：
  // isNewUser 是登录响应里的本次性字段，从来没人清过它，是个会一直躺在
  // userInfo 里的脏数据。趁这次读到的机会清掉，避免以后又误判。
  if (uInfo.isNewUser && uInfo.nicknameApplied) {
    delete uInfo.isNewUser
    setUserInfo(uInfo)
    return
  }
  if (!uInfo.isNewUser) return
  if (!hasDefaultNickname(uInfo)) {          // 已改过昵称 → 永久不再弹
    uInfo.nicknameApplied = true
    delete uInfo.isNewUser
    setUserInfo(uInfo)
    return
  }
  nicknamePromptVisible.value = true
}

/** 昵称为空，或仍是后端发的默认昵称「用户 + 后 6 位」→ 视为还没起过名字 */
function hasDefaultNickname(uInfo) {
  const n = String((uInfo && uInfo.nickname) || '').trim()
  if (!n) return true
  if (n === '家人') return true                       // 旧版默认昵称
  // 默认昵称 = 「用户」+ openId 后 6 位；真实 openid 含 - 和 _，字符类要放宽
  return /^用户[\w-]{1,12}$/.test(n)
}

// 组件「保存」后本地缓存已是新昵称，直接同步一次，避免同一次会话内再弹
uni.$on('profile:nickname-saved', () => { nicknamePromptVisible.value = false })
onUnload(() => { unmounted = true })
uni.$on('cart:changed', loadAll)
uni.$on('family:changed', loadAll)
</script>

<style scoped>
/* ============= 甜美少女粉·首页 ============= */
.index-page {
  padding-bottom: 14%;
  position: relative;
  /* 底部兜底：极浅薄荷渐变，长列表滚到底时不会露出突兀的纯色边 */
  background: var(--c-bg);
  background-image: var(--g-ethereal);
  background-attachment: fixed;
  min-height: 100vh;
}
/* 顶部渐变层默认样式在 App.vue 的 .hero-bg（fixed 400rpx + mask 衰减） */
/* 内容层：必须在渐变层之上 */
.hero-layer { position: relative; z-index: 1; }

/* 顶部 banner：浮在珊瑚粉渐变上的透明毛玻璃区 */
.chef-banner {
  padding: 4% 5%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.banner-left { display: flex; align-items: center; gap: 3%; flex: 1; min-width: 0; }
.banner-right { display: flex; align-items: center; padding-left: 2%; opacity: .85; }
.banner-emoji-wrap {
  width: 96rpx; height: 96rpx;
  background: rgba(255,255,255,.45);
  border: 2rpx solid rgba(255,255,255,.55);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  box-shadow: var(--glow-pink);
  flex-shrink: 0;
  animation: sk-breath 2.6s ease-in-out infinite;
}
.banner-info { display: flex; flex-direction: column; flex: 1; min-width: 0; }
.banner-title {
  font-size: var(--t-xl);
  font-weight: 300;
  /* 顶部深粉渐变段 → 纯白 + 双层阴影（见 App.vue 的 .text-on-dark 规范）
     白 on #FF7B94 实测 ≈2.47:1，粉色光晕是同色系提不了反差，
     必须叠一层深玫瑰灰暗边才有真正的边缘对比度。 */
  color: #FFFFFF;
  letter-spacing: 2rpx;
  text-shadow: 0 1rpx 2rpx rgba(122,74,90,0.35), 0 2rpx 10rpx rgba(255,123,148,0.5);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}
.banner-sub {
  font-size: 68.75%;
  font-weight: 300;
  /* 深粉段副标题：半透明白 + 同款双层阴影 */
  color: rgba(255,255,255,0.92);
  text-shadow: 0 1rpx 2rpx rgba(122,74,90,0.30), 0 2rpx 10rpx rgba(255,123,148,0.45);
  margin-top: 6rpx;
  letter-spacing: 1rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

/* 双入口大按钮 */
.entries {
  display: flex; align-items: stretch;
  gap: 5%; padding: 4% 2.5%;
}
.entry-burst {
  flex: 1; min-width: 0; display: flex; box-sizing: border-box;
}
.entry-burst :deep(.tap-burst) {
  width: 100%;
  overflow: hidden;
  border-radius: 32rpx;
  box-sizing: border-box;
}
.entry-btn {
  width: 100%;
  box-sizing: border-box;
  border-radius: 32rpx;
  padding: 8% 4% 4%;
  min-height: 300rpx;
  display: flex; flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  text-align: center;
  position: relative;
  overflow: hidden;
  color: white;
}
/* 双入口：统一到珊瑚粉色系，用「深浅」区分主次
   左卡＝深玫瑰灰渐变（主入口，60→80）
   右卡＝亮珊瑚粉渐变（次入口，40→50） */
.entry-menu   {
  background: linear-gradient(135deg, #FF7B94 0%, #FF5D7E 100%);
  box-shadow: 0 8rpx 24rpx rgba(255,123,148,0.26);
}
.entry-custom {
  background: linear-gradient(135deg, #FFC2CF 0%, #FFA8BC 100%);
  box-shadow: 0 8rpx 24rpx rgba(255,123,148,0.20);
}
.entry-icon-wrap {
  width: 96rpx; height: 96rpx;
  background: rgba(255,255,255,.22);
  border: 2rpx solid rgba(255,255,255,.4);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  padding: 14rpx;
  backdrop-filter: blur(16rpx);
  -webkit-backdrop-filter: blur(16rpx);
}
/* 亮珊瑚粉卡（次入口）文字需深色，否则白字不可读；
   深玫瑰灰卡文字保持白色。基类默认白，卡特例覆盖。 */
.entry-info {
  display: flex; flex-direction: column; align-items: center;
  gap: 14rpx;
  /* 图标（.entry-icon-wrap）保持原位不动；主/副标题整块往下挪 40rpx。
     ⚠️ 不要改成 .entry-btn 的 justify-content:center —— 那会把图标一起推下去。
     卡片 min-height 300rpx，图标 96rpx + 下挪 40rpx + 文字约 90rpx + 上内边距，
     仍留约 45rpx 底部余量，不会溢出。 */
  margin-top: 40rpx;
}
.entry-title { font-size: var(--t-lg); font-weight: 400; letter-spacing: 1rpx; color: #FFFFFF;
  /* 白字 on 珊瑚粉：暗边 + 粉晕双层（见 App.vue .text-on-dark 规范） */
  text-shadow: 0 1rpx 2rpx rgba(122,74,90,0.35), 0 2rpx 10rpx rgba(255,123,148,0.5); }
.entry-sub { font-size: 75%; font-weight: 300; opacity: .92; color: #FFFFFF;
  text-shadow: 0 1rpx 2rpx rgba(122,74,90,0.30), 0 2rpx 10rpx rgba(255,123,148,0.45); }
.entry-custom .entry-title,
.entry-custom .entry-sub { color: var(--c-text); text-shadow: none; }
.entry-custom .entry-icon-wrap {
  background: rgba(255,255,255,0.55);
  border-color: rgba(255,255,255,0.65);
}
.entry-custom .entry-sub { opacity: 1; font-weight: 400; }

/* 未登录时遮罩：覆盖在按钮上，配合 .disabled 灰显 */
.entry-mask {
  position: absolute; inset: 0;
  background: rgba(0, 0, 0, 0.32);
  display: flex; align-items: center; justify-content: center;
  border-radius: 32rpx;
}
.entry-lock { color: white; font-size: 68.75%; font-weight: 400; letter-spacing: 2rpx; }
.entry-btn.disabled {
  filter: grayscale(.4);
  opacity: .8;
}

/* 未登录时的提示卡 + 登录按钮 — 改用毛玻璃 */
.login-banner {
  margin: 0 4% 4%;
  padding: 6% 5%;
  display: flex; flex-direction: column; align-items: center; gap: 4%;
  border-radius: 32rpx;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  box-shadow: var(--glow-soft);
}
.login-banner-title { font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.login-banner-hint { font-size: 68.75%; font-weight: 300; color: var(--c-text-2); }
.login-banner-btn {
  width: 70%;
  height: 80rpx;
  margin-top: 3%;
  border-radius: 999rpx !important;
  background: var(--g-primary) !important;
  color: white !important;
  font-size: var(--t-md) !important;
  font-weight: 400 !important;
  letter-spacing: 4rpx !important;
  box-shadow: var(--glow-pink);
}
.login-banner-btn[disabled] { opacity: .7; }

/* 概览卡：毛玻璃 */
.overview-card {
  margin: 0 4% 4%;
  padding: 4% 5%;
  border-radius: 32rpx;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  box-shadow: var(--glow-soft);
}
.overview-title {
  display: flex; align-items: center; gap: 2%;
  font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose);
  letter-spacing: 1rpx;
  margin-bottom: 4%;
}
.overview-stats { display: flex; align-items: center; justify-content: space-around; }
.stat-item { display: flex; flex-direction: column; align-items: center; }
.stat-num { font-size: 125%; font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.stat-label { font-size: 68.75%; font-weight: 300; color: var(--c-text-2); margin-top: 4rpx; }
.stat-divider { width: 1rpx; height: 60rpx; background: rgba(122,74,90,.2); }

/* 今日菜单：订单卡用毛玻璃 */
.menu-list { padding: 0 4%; display: flex; flex-direction: column; gap: 32rpx; }
.order-group {
  border-radius: 32rpx;
  padding: 4%;
  background: rgba(255,255,255,0.6);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  box-shadow: var(--glow-soft);
}
.order-group.cancelled { opacity: .65; filter: grayscale(.3); }
.og-head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 2%;
}
.og-no {
  display: flex; align-items: center; gap: 2%;
  font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose);
  letter-spacing: 1rpx;
}
.og-status {
  font-size: 22rpx; padding: 4rpx 14rpx; border-radius: 999rpx;
  font-weight: 400;
  white-space: nowrap;
}
.og-status.st-0  { background: #FFF0F3; color: #B4637A; }
.og-status.st-1  { background: #E3F7EE; color: var(--c-success); }
.og-status.st--1 { background: var(--c-danger-bg); color: var(--c-danger); }
.og-meta {
  display: flex; align-items: center; gap: 2%;
  font-size: 68.75%; color: var(--c-text-2); margin-bottom: 2%;
}
.og-user { font-weight: 500; color: var(--c-deep-rose); }
.og-time { font-weight: 300; }
/* 订单菜品数量统计 */
.og-stats {
  display: flex; align-items: center; flex-wrap: wrap; gap: 2%;
  margin: 2% 0;
  padding: 3% 4%;
  background: rgba(255,255,255,0.4);
  border-radius: 16rpx;
  font-size: 24rpx;
  font-weight: 400;
  border: 1rpx solid rgba(255,255,255,0.4);
}
.og-stat { white-space: nowrap; color: var(--c-deep-rose); font-weight: 400; }
.og-stat.ok   { color: var(--c-success); }
.og-stat.no   { color: var(--c-danger); }
.og-stat.wait { color: #B4637A; }
.og-foot {
  display: flex; justify-content: space-between; align-items: center;
  padding-top: 3%; border-top: 1rpx solid rgba(122,74,90,.18);
}
.og-total { font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.og-arrow { font-size: 68.75%; font-weight: 300; color: var(--c-text-2); }

/* 骨架卡：也用毛玻璃 */
.menu-skeleton { padding: 0 4%; display: flex; flex-direction: column; gap: 32rpx; }
.menu-skeleton .card {
  background: rgba(255,255,255,0.4);
  border: 1rpx solid rgba(255,255,255,0.4);
  box-shadow: var(--glow-soft);
}

/* 空态卡 */
.empty-state {
  margin: 0 4%;
  text-align: center;
  padding: 12% 6%;
  border-radius: 32rpx;
  background: rgba(255,255,255,0.45);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.45);
}
.empty-icon { display: block; margin: 0 auto 3%; }
.empty-title { display: block; font-size: var(--t-lg); color: var(--c-deep-rose); font-weight: 500; letter-spacing: 1rpx; }
.empty-hint  { display: block; font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); margin-top: 3%; }
</style>