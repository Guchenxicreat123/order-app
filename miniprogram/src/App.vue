<script setup>
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'
import { ref } from 'vue'
import {
  getChefStatus, getToken, isTokenExpired, forceLogin, onAuthLogin,
  getMyFamilies, switchFamily, getMyCart, getUserInfo, getMyOrders,
  getPublicDishesWithIngredients
} from './utils/request.js'
import { cachedFetch } from './utils/cache.js'
import { warmPushConfig, ensureSubscribeBulk, isPushConfigReady, CHEF_QUOTA_ON_CONFIRM } from './utils/subscribe.js'

// 把 vue/uni-app 框架内部抛出的「e is not a function」之类未捕获异常
// 降级为 console.warn，避免污染控制台/中断渲染。
// 真正的业务错误（HTTP 4xx/5xx）仍由各页面的 catch 块处理，这里不拦截。
function _onRuntimeError(label, err) {
  try {
    const msg = (err && (err.message || err.errMsg)) || String(err)
    // 只兜住典型「callback 不是函数」类错误，不掩盖其它业务异常
    if (/is not a function|callback/i.test(msg)) {
      console.warn(`[runtime:${label}] ignored`, msg)
      return true
    }
  } catch (_) {}
  return false
}
// #ifdef MP-WEIXIN
uni.onError && uni.onError((err) => { _onRuntimeError('onError', err) })
uni.onUnhandledRejection && uni.onUnhandledRejection((evt) => {
  _onRuntimeError('unhandledRejection', evt && evt.reason)
})
// #endif

const ACTIVE_FAMILY_KEY = 'activeFamilyId'
const USER_KEY = 'userInfo'
const WELCOMED_KEY = 'welcomed_v1'

/** 公共菜缓存 key —— 小程序启动预加载到 storage，详情页/列表页 0ms 读取 */
const PUBLIC_DISHES_KEY = 'public_dishes_all_with_ingredients'

/**
 * 后台预加载公共菜库（含配方）—— 不阻塞 UI，不等结果
 * 用法：
 *   - 首次启动：3 秒缓存 TTL 内，二次进入直接 0ms 读 storage
 *   - 管理员修改了公共菜：在管理端调 invalidate('public_*') 主动失效
 *   - onShow 时距上次预加载超过 10 分钟才再次拉取，避免后台隐藏后频繁请求
 */
let _lastPrefetchAt = 0
function prefetchPublicLibrary() {
  const now = Date.now()
  if (now - _lastPrefetchAt < 10 * 60 * 1000) return  // 10 分钟内不重复
  _lastPrefetchAt = now
  cachedFetch(PUBLIC_DISHES_KEY, () => getPublicDishesWithIngredients(), 30 * 60 * 1000)
    .catch(() => {
      // 静默失败：不影响 UI
    })
}

// 注：登录后的「完善资料（选头像/改昵称）」弹层已按需求移除，
// 登录成功后直接进入主画面，不再拦截。相关组件文件仍保留但不再引入。

/** token 缺失或已过期 → 需要重新登录（避免带着死 token 发请求产生 401） */
function needLogin() {
  const tok = getToken()
  return !tok || isTokenExpired(tok)
}

// 说明：App 现在不做任何自动登录，登录统一由「立即登录」按钮触发，
// 因此这里不再需要 isLogoutByUser/clearLogoutFlag。
// （logout_by_user 标记仍由 utils/request.js 的 401 分支使用，语义未变）

/** 判断当前页是否是 TabBar 页面 —— 只有 TabBar 页才能 setTabBarBadge。
 *  使用 uni.getTabBarPages()（mp-weixin 3.17+）动态获取，零硬编码。
 *  失败兜底用 getCurrentPages() 路径硬编码匹配。 */
function isOnTabBarPage() {
  try {
    const pages = getCurrentPages()
    if (!pages || pages.length === 0) return false
    const cur = pages[pages.length - 1]
    const path = cur && (cur.route || (cur.$page && cur.$page.route)) || ''
    if (!path) return false
    // 优先用 uni 内置 API（3.17+ 返回当前 TabBar 页面路径列表，零硬编码）
    try {
      if (typeof uni !== 'undefined' && typeof uni.getTabBarPages === 'function') {
        const list = uni.getTabBarPages() || []
        if (list && list.length) return list.includes(path)
      }
    } catch (e) {}
    // 兜底：与 pages.json tabBar.list.pagePath 一致
    const fallback = ['pages/index/index', 'pages/cart/cart', 'pages/mine/mine']
    return fallback.includes(path)
  } catch (e) {
    return false
  }
}

/** 计算购物车数量，刷新 tabBar 角标 */
async function refreshCartBadge() {
  // 非 TabBar 页面（welcome / family / login 等）调 setTabBarBadge 会报"not TabBar page"错误
  if (!isOnTabBarPage()) return
  if (needLogin()) {
    try { uni.removeTabBarBadge({ index: 1, fail: () => {} }) } catch (e) {}
    return
  }
  try {
    const items = await getMyCart()
    const count = (items || []).reduce((s, i) => s + (i.quantity || 1), 0)
    if (count > 0) {
      uni.setTabBarBadge({ index: 1, text: String(count), fail: () => {} })
    } else {
      uni.removeTabBarBadge({ index: 1, fail: () => {} })
    }
  } catch (e) {
    try { uni.removeTabBarBadge({ index: 1, fail: () => {} }) } catch (_) {}
  }
}

/** 全局可调用：刷新购物车角标（被任意页面在加车/结算时调用） */
uni.$refreshCartBadge = refreshCartBadge

// 监听来自页面的刷新请求
uni.$on('cart:changed', refreshCartBadge)

onLaunch(async () => {
  // 公共菜库后台预加载（不阻塞首屏）
  prefetchPublicLibrary()

  const firstOpen = !uni.getStorageSync(WELCOMED_KEY)
  // 注意：这里【不再】自动登录。
  // 需求：初次打开小程序必须先展示登录入口，等用户点「立即登录」才真正发起登录；
  // 之前是后台静默 forceLogin，用户还没点就已经登进去了。
  if (firstOpen) {
    // 首次进入：先展示启动页，登录/家庭引导交给欢迎页
    uni.setStorageSync(WELCOMED_KEY, 1)
    setTimeout(() => uni.reLaunch({ url: '/pages/welcome/welcome' }), 50)
    return
  }
  if (getToken()) {
    // 只同步家庭状态，【不】再强制跳家庭页：
    // 新用户没有家庭时也允许先浏览主画面，点菜入口会提示建家庭。
    await checkFamilyStatus()
    // 错开一点，避免与上面 getMyFamilies 同时打满 CDN/网关
    setTimeout(() => {
      checkChefStatus()
      refreshCartBadge()
      // 预热订阅消息配置（拿模板 ID），这样用户点「结算 / 确认」时能直接弹微信授权面板
      warmPushConfig()
    }, 250)
    // 待确认提醒：等启动页跳完再弹（启动页播放期间 showModal 会被 reLaunch 吞掉）
    setTimeout(() => afterBoot(promptPendingOrders), 1200)
  }
})

onShow(async () => {
  // 回到前台时也尝试预加载（10 分钟内不重复）
  prefetchPublicLibrary()

  // 回到前台：没有 token 时【不再】自动登录，统一由「立即登录」按钮触发；
  // 已有 token 则继续刷新家庭状态与角标。
  if (getToken()) {
    checkFamilyStatus().catch(() => {})
    // 错开 cart 请求，避免和 family 同时打 CDN
    setTimeout(() => refreshCartBadge(), 250)
    // 回到前台也算「进入小程序」：90 秒冷却兜底，不会和 onLaunch 重复弹
    setTimeout(() => afterBoot(promptPendingOrders), 1200)
  } else {
    refreshCartBadge()
  }
})

// 登录成功后补一次检查（首次进入时可能还没登录，拿不到 token）
onAuthLogin(() => { setTimeout(() => afterBoot(promptPendingOrders), 1200) })

onHide(() => {})

/** 返回 true 表示需要跳转到家庭页。同时把最新家庭列表同步到 userInfo storage */
async function checkFamilyStatus() {
  try {
    const list = await getMyFamilies() || []
    if (list.length === 0) return true
    const cached = uni.getStorageSync(ACTIVE_FAMILY_KEY)
    const active = list.find(f => f.active) || list.find(f => f.familyId === cached) || list[0]
    if (!cached || (cached !== active.familyId && !list.find(f => f.familyId === cached && f.active))) {
      try { await switchFamily(active.familyId) } catch (e) {}
      uni.setStorageSync(ACTIVE_FAMILY_KEY, active.familyId)
    }
    // 同步家庭列表到 userInfo storage，保证所有页面读到最新数据
    const userInfo = uni.getStorageSync(USER_KEY) || {}
    userInfo.families = list
    userInfo.activeFamilyId = active ? active.familyId : null
    if (active && !userInfo.userId) userInfo.userId = null
    uni.setStorageSync(USER_KEY, userInfo)
    return false
  } catch (e) {
    return true
  }
}

async function checkChefStatus() {
  try {
    const data = await getChefStatus()
    if (!data.hasChef) {
      // 静默，不打扰用户
    }
  } catch (e) {}
}

// ========== 主厨待确认订单提醒 ==========
// 主厨每次进入小程序时，若存在待确认（status===0）的订单，
// 弹窗引导到「订单确认」画面；点「去确认」时顺手补一次微信订阅额度。
//
// 去重：按订单 ID 记在内存里（本次运行期有效）。点过「稍后」或「去确认」的订单
// 不再重复提示；只有**新出现的**待确认订单才会再次弹窗。
// 不落 storage —— 完全退出重进小程序后应重新提醒一次。
let pendingPrompting = false   // 请求进行中，避免并发重复弹
const dismissedOrderIds = new Set()   // 已提示过的订单 ID（「稍后」或「去确认」）

/** 当前是否停在「订单确认」页（在该页就不再弹） */
function onConfirmOrdersPage() {
  try {
    const pages = (typeof getCurrentPages === 'function') ? getCurrentPages() : []
    if (!pages.length) return false
    const route = pages[pages.length - 1].route || ''
    return route.indexOf('confirm-orders') >= 0
  } catch (e) {
    return false
  }
}

/** 当前是否还在启动页（启动页有自己的时序，等它跳完再弹，避免被 reLaunch 吞掉） */
function onWelcomePage() {
  try {
    const pages = (typeof getCurrentPages === 'function') ? getCurrentPages() : []
    if (!pages.length) return false
    const route = pages[pages.length - 1].route || ''
    return route.indexOf('welcome') >= 0
  } catch (e) {
    return false
  }
}

/** 延后到启动页离开后再执行（启动页最多播 MAX_BOOT_MS） */
function afterBoot(fn, tries) {
  const n = (tries === undefined) ? 40 : tries   // 40 × 500ms = 最多等 20s
  if (n <= 0 || !onWelcomePage()) { fn(); return }
  setTimeout(() => afterBoot(fn, n - 1), 500)
}

async function promptPendingOrders() {
  if (pendingPrompting) return
  if (!getToken()) return
  if (onConfirmOrdersPage()) return
  if (onWelcomePage()) return

  pendingPrompting = true
  try {
    // 只有主厨才提示
    const chef = await getChefStatus()
    if (!chef || !chef.isMe) return

    const orders = await getMyOrders() || []
    // 还有菜没确认的待确认订单（整单已确认/撤销的不算）
    const pending = orders.filter(o => o.status === 0 &&
      Math.max(0, (o.itemCount || 0) - (o.confirmedCount || 0) - (o.rejectedCount || 0)) > 0)
    if (!pending.length) { dismissedOrderIds.clear(); return }

    // 只提示「还没提示过的」新订单；全部提示过就不再打扰
    if (!pending.some(o => !dismissedOrderIds.has(o.orderId))) return

    const orderCount = pending.length
    uni.showModal({
      title: '有待确认的订单',
      content: orderCount === 1
        ? '有 1 个订单还没确认，去看看要做什么菜吧'
        : `有 ${orderCount} 个订单还没确认，去看看要做什么菜吧`,
      confirmText: '去确认',
      cancelText: '稍后',
      confirmColor: '#FF7B94',
      success: (res) => {
        // 无论「去确认」还是「稍后」，这批订单都不再重复提示
        pending.forEach(o => dismissedOrderIds.add(o.orderId))
        if (res.confirm) {
          // 主厨收的是「新订单」提醒：借这次点击补订阅额度，保证后续订单能推达。
          // showModal 的按钮回调算用户手势，微信认，面板能正常弹。
          if (isPushConfigReady('CHEF')) ensureSubscribeBulk('CHEF', CHEF_QUOTA_ON_CONFIRM)
          uni.navigateTo({ url: '/pages/confirm-orders/confirm-orders' })
        }
      },
    })
  } catch (e) {
    // 静默失败，不打扰用户
  } finally {
    pendingPrompting = false
  }
}
</script>

<style>
/* ============= 勺意 · 设计令牌（Design Tokens） ============= */
/* 全百分比布局：
   - page 设 font-size = 4.2667vw（≈ 屏宽 750rpx → 32rpx 基准）
   - 所有 token font-size 以 % 表示（100% = 32rpx）
   - 布局 width/margin/padding/gap 全部按 % */
page {
  font-size: 4.2667vw;

  /* ============ 甜美少女粉渐变主题 · 色阶 ============
     珊瑚粉 #FF7B94 → 薄荷白 #DAFFFB，底色 #FDF8F9。
     深粉只用于「重色锚点」：主按钮 / 选中态 / 关键数字 / 顶部渐变层。
     浅色区一律深玫瑰灰 #7A4A5A 主标题 + #333333 正文（浅底上白字对比度不足，禁止）。 */
  --c-primary:        #FF7B94;    /* 珊瑚粉 · 主色 */
  --c-primary-dark:   #FF5D7E;    /* 深珊瑚 · 按下/强调 */
  --c-primary-deep:   #7A4A5A;    /* 深玫瑰灰 · 关键文字落点 */
  --c-primary-light:  rgba(255,123,148,0.14); /* 主色浅底（选中底） */
  --c-primary-lighter:#DAFFFB;    /* 薄荷白 · 最浅底 */
  --c-bg:             #FDF8F9;    /* 页面兜底底色 */
  --c-card:           rgba(255,255,255,0.60);  /* 玻璃卡底色（配 blur） */
  --c-card-solid:     #FFFFFF;    /* 不透明白卡（低端机降级/弹层） */
  /* 文字色（浅底上禁止纯白；主标题深玫瑰灰，正文深灰，副标题半透明） */
  --c-text:           #7A4A5A;    /* 深玫瑰灰 · 主标题 */
  --c-text-body:      #333333;    /* 正文深灰 */
  /* ⚠️ 实测对比度（白底）：.60 → 2.83:1、.48 → 2.22:1，都低于正文门槛。
     规范给的 0.6 偏「观感」，本项目把 --c-text-2/-3 用于成片的说明文字，
     照抄会让浅色区整片发虚。故上提到 .75 / .62（3.9:1 / 3.1:1），
     既保住柔和感又跨过 AA-large。需要更淡的装饰字才用 .55 及以下。 */
  --c-text-2:         rgba(122,74,90,0.75);  /* 副标题（规范原值 0.6，为可读性上提） */
  --c-text-3:         rgba(122,74,90,0.62);  /* 辅助字/占位 */
  --c-line:           rgba(122,74,90,0.18);  /* 分割线/描边（浅底上太淡会消失） */

  /* ===== 语义色层（点8）：功能状态色与主题色解耦 =====
     ⚠️ 规则：主题色只负责「品牌/强调」，状态一律走语义色。
        这样以后换皮（粉→蓝→任何色）时，红绿灯语义不会跟着漂。 */
  --c-chef:           #FF7B94;    /* 主厨身份粉 */
  --c-chef-light:     #FFE7EC;
  --c-info:           #B4637A;    /* 信息/待处理 · 玫瑰灰（沿用原 pending） */
  --c-info-bg:        #FFF0F3;
  --c-pending:        #B4637A;    /* 兼容旧名 → 同 --c-info */
  --c-pending-bg:     #FFF0F3;
  --c-success:        #22B573;    /* 成功/已确认 */
  --c-success-bg:     #E3F7EE;
  --c-warning:        #F5A623;    /* 警告/待补全 */
  --c-warning-bg:     #FFF6E5;
  --c-danger:         #E5484D;    /* 危险/驳回红 */
  --c-danger-bg:      #FFE4E2;
  --c-neutral:        rgba(122,74,90,0.75);  /* 中性/禁用文字 */
  --c-neutral-bg:     rgba(255,240,243,.8);  /* 中性底 */
  --c-gold:           #F5A623;    /* 兼容旧名 → 同 --c-warning */

  /* ===== 叠层预算（点3）：背景 → 卡片 → 浮层，三级必须拉开 =====
     亮色主题最怕「处处一样亮」，下面三级各配「底色 + 描边 + 阴影」一套，
     任何可点组件至少要有「阴影/描边/圆角」中的两项，否则在浅底上会「点不下去」。 */
  /* L1 背景层：最浅（薄荷/兜底底） */
  --l1-bg:      transparent;
  /* L2 卡片层：实白 or 半透白 + 细白描边 + 粉色光晕 */
  --l2-bg:      #FFFFFF;
  --l2-border:  1rpx solid rgba(255,255,255,0.85);
  --l2-shadow:  0 6rpx 24rpx rgba(255,123,148,.13), 0 1rpx 0 rgba(255,255,255,.95) inset;
  /* L3 浮层（弹窗/底部栏/胶囊）：更白更实 + 更重光晕，明确「浮在最上面」 */
  --l3-bg:      #FFFFFF;
  --l3-border:  1rpx solid rgba(255,255,255,0.95);
  --l3-shadow:  0 16rpx 56rpx rgba(122,74,90,.18), 0 2rpx 0 rgba(255,255,255,1) inset;

  /* 间距（仍用 rpx，保持紧凑视觉感） */
  --s-1: 8rpx;  --s-2: 16rpx; --s-3: 24rpx;
  --s-4: 32rpx; --s-5: 40rpx; --s-6: 56rpx;

  /* 字号（rpx — 直接按屏宽自适应，无级联不确定性） */
  --t-xs:  22rpx;
  --t-sm:  24rpx;
  --t-md:  28rpx;
  --t-lg:  32rpx;
  --t-xl:  36rpx;
  --t-xxl: 44rpx;

  /* 圆角（点5 呼吸感：统一一档半径体系，禁止页面再自造 28/36/44 等杂值）
     卡片 32rpx 是「少女治愈感」的甜点位；胶囊一律 999rpx。 */
  --r-xs:   12rpx;
  --r-sm:   20rpx;
  --r-card: 32rpx;
  --r-lg:   40rpx;
  --r-pill: 999rpx;

  /* 阴影（粉色同色系光晕，禁用黑色） */
  --sh-card: 0 8rpx 32rpx rgba(255,123,148,.15);
  --sh-fab:  0 8rpx 24rpx rgba(255,123,148,.35);

  /* 品牌渐变 */
  --g-primary: linear-gradient(135deg, #FF7B94 0%, #FF5D7E 100%);
  --g-chef:    linear-gradient(135deg, #FF7B94 0%, #FFA8BC 100%);
  --g-warm:    linear-gradient(135deg, #FFA8BC 0%, #FF7B94 100%);

  background: var(--c-bg);
  color: var(--c-text);
  font-family: 'PingFang SC', 'Helvetica Neue', -apple-system, sans-serif;

  /* ========== 甜美少女粉 & 毛玻璃 token ========== */
  /* 主背景渐变：极浅薄荷微渐变（接近纯白）。
     ⚠️ 不用深粉铺满全屏！深粉只出现在页面顶部固定渐变层 --g-hero 上，
        否则长列表滚到底部时白卡片会和白底/白字糊在一起。 */
  --g-ethereal:      linear-gradient(135deg, #EDFFFF 0%, #DAFFFB 100%);
  --g-ethereal-soft: linear-gradient(135deg, #FDF8F9 0%, #EDFFFF 100%); /* 更柔，弱背景用 */
  --g-hero:          linear-gradient(135deg, #FF7B94 0%, #DAFFFB 100%); /* 顶部渐变层（配 fixed + 衰减） */
  --g-hero-tall:     linear-gradient(180deg, #FF7B94 0%, rgba(255,123,148,.45) 46%, #DAFFFB 100%);
  --g-aurora:        linear-gradient(135deg, rgba(255,168,188,.42) 0%, rgba(218,255,251,.28) 100%); /* 半透弥散光 */

  /* ===== 弥散光（点1）：浅色区也要有颜色，否则滚动到下半屏就「一片平」 =====
     用 radial-gradient 而非图片，零资源、可随屏宽缩放。 */
  --blob-pink:  radial-gradient(circle, rgba(255,123,148,.20) 0%, rgba(255,123,148,0) 70%);
  --blob-rose:  radial-gradient(circle, rgba(255,168,188,.24) 0%, rgba(255,168,188,0) 70%);
  --blob-mint:  radial-gradient(circle, rgba(218,255,251,.55) 0%, rgba(218,255,251,0) 72%);
  --blob-white: radial-gradient(circle, rgba(255,255,255,.50) 0%, rgba(255,255,255,0) 70%);

  /* 粉色光晕阴影（规范 rgba(255,123,148,.15)，绝不使用黑色投影）
     点2：全部改成「外光晕 + 内高光」双层 —— 外光晕给悬浮感，
          内高光（inset 白）在卡片上沿收出一道边，这是玻璃拟物最有效的提升点，
          也让白卡在白底上不会「贴」住背景。 */
  --glow-pink:      0 8rpx 32rpx rgba(255,123,148,.15), 0 1rpx 0 rgba(255,255,255,.95) inset;
  --glow-blue:      0 8rpx 24rpx rgba(255,168,188,.26), 0 1rpx 0 rgba(255,255,255,.95) inset; /* 兼容旧名 · 浅粉柔光 */
  --glow-soft:      0 4rpx 20rpx rgba(255,123,148,.10);
  --glow-deep:      0 12rpx 48rpx rgba(122,74,90,.16);
  --glow-press:     0 3rpx 12rpx rgba(255,123,148,.20);  /* 按下时收紧，制造「压下去」 */
  --glow-float:     0 16rpx 56rpx rgba(122,74,90,.18), 0 2rpx 0 rgba(255,255,255,1) inset; /* 浮层 */

  /* 毛玻璃：高透白 + backdrop-filter blur
     ⚠️ 小程序（尤其安卓）backdrop-filter 支持不稳定，
        所有使用处都自带 rgba 半透明白底作为降级，绝不能去掉那行 background */
  --glass-bg:    rgba(255,255,255,0.60);   /* 安卓降级就是这个值（规范 0.6） */
  --glass-bg-2:  rgba(255,255,255,0.78);
  --glass-bg-3:  rgba(255,255,255,0.42);   /* 弱玻璃，叠在彩色背景上 */
  --glass-blur:  blur(20rpx);
  --glass-blur-strong: blur(40rpx);
  --glass-border: 1rpx solid rgba(255,255,255,0.8);  /* 规范玻璃边缘高光 */

  /* 双层文字色规范（亮色主题避坑核心）
     ⚠️ 顶部深粉区用 .text-on-dark（纯白 + 粉色发光）；
        下方浅色区【绝对禁止】白字，用 --c-text / --c-text-body / --c-text-2
        （.text-on-light 类同义）。 */
  --c-on-glass:   #FFFFFF;                    /* 仅深粉/薄荷渐变底上的白字 */
  --c-on-glass-2: rgba(255,255,255,0.85);
  --c-on-glass-3: rgba(255,255,255,0.65);
  --c-deep-rose:  #7A4A5A;                    /* 浅粉底上的深字（深玫瑰灰，≈7.8:1） */
  --c-deep-blue:  #7A4A5A;                    /* 兼容旧名 · 现为深玫瑰灰 */
  /* 白字发光（顶部深粉区专用），与 .text-on-dark 一致 */
  --text-glow: 0 1rpx 2rpx rgba(122,74,90,0.35), 0 2rpx 10rpx rgba(255,123,148,0.5);
}

/* ============= 全局动画（keyframes 集合） ============= */
@keyframes sk-shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
@keyframes sk-bubble {
  0%   { transform: translateY(0) scale(.6); opacity: 0; }
  30%  { opacity: .7; }
  100% { transform: translateY(-40rpx) scale(1.1); opacity: 0; }
}
@keyframes sk-breath {
  0%, 100% { transform: scale(1); opacity: .9; }
  50%      { transform: scale(1.06); opacity: 1; }
}
@keyframes sk-heartbeat {
  0%, 100% { transform: scale(1); }
  20%      { transform: scale(1.18); }
  50%      { transform: scale(.95); }
}
@keyframes sk-shake {
  0%, 100% { transform: rotate(0); }
  20%      { transform: rotate(-7deg); }
  40%      { transform: rotate(6deg); }
  60%      { transform: rotate(-3deg); }
  80%      { transform: rotate(2deg); }
}
@keyframes sk-spoon-swing {
  0%, 100% { transform: rotate(-14deg); }
  50%      { transform: rotate(14deg); }
}
@keyframes sk-burst-fly {
  0%   { opacity: 0; transform: translate(0,0) scale(.4) rotate(0); }
  20%  { opacity: 1; }
  100% { opacity: 0; transform: translate(var(--dx,0), var(--dy,-80rpx)) scale(1.2) rotate(var(--rot,0deg)); }
}
@keyframes sk-bounce-in {
  0%   { transform: scale(.6); opacity: 0; }
  60%  { transform: scale(1.12); opacity: 1; }
  100% { transform: scale(1); }
}
@keyframes sk-fade-up {
  0%   { transform: translateY(24rpx); opacity: 0; }
  100% { transform: translateY(0); opacity: 1; }
}
@keyframes sk-slide-out-left {
  0%   { transform: translateX(0); opacity: 1; }
  100% { transform: translateX(-120%); opacity: 0; }
}
@keyframes sk-spin {
  to { transform: rotate(360deg); }
}
/* 白色四角星光闪烁：缩放 + 透明度双通道，比单纯 opacity 更有「微光」质感 */
@keyframes twinkle {
  0%, 100% { opacity: 0;    transform: scale(.6); }
  50%      { opacity: .95;  transform: scale(1.15); }
}

/* ============= 通用工具类 ============= */
/* 普通卡片：亮色主题下用「实白 + 粉色光晕」而非半透明玻璃。
   理由：低端安卓 backdrop-filter 失效时 60% 白底会显脏；而这里是浅色区，
   实白卡片本身就有足够的空间层次，且少一层滤波 = 滚动更顺。
   需要玻璃质感的地方请显式加 .glass-card。 */
.card {
  background: var(--c-card-solid);   /* #FFFFFF */
  border: var(--l2-border);          /* 点2：细白高光描边，替代裸硬编码 */
  border-radius: var(--r-card);
  padding: 28rpx;
  margin: 24rpx;
  box-shadow: var(--l2-shadow);      /* 点2：外光晕 + 内高光双层 */
}
.btn-primary {
  background: var(--g-primary) !important;
  border: none !important;
  color: white !important;
  border-radius: var(--r-pill) !important;
  font-weight: 500;
  letter-spacing: 2rpx;
  line-height: 1.5;
  box-shadow: var(--glow-pink) !important; /* 珊瑚粉同色光晕（规范 rgba(255,123,148,.15)） */
}
.btn-primary[disabled],
.btn-primary.disabled {
  background: rgba(122,74,90,.45) !important;
  box-shadow: none !important;
  opacity: .6;
  color: white !important;
}
.btn-chef {
  background: var(--g-chef) !important;
  border: none !important;
  color: white !important;
  border-radius: var(--r-pill) !important;
  font-weight: 400;
  letter-spacing: 2rpx;
  line-height: 1.5;
}
.btn-danger {
  background: var(--c-danger) !important;
  border: none !important;
  color: white !important;
  border-radius: var(--r-pill) !important;
  font-weight: 400;
  letter-spacing: 2rpx;
  line-height: 1.5;
}
.btn-ghost {
  background: rgba(255,255,255,0.60) !important;   /* 毛玻璃降级底色 */
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid var(--c-line) !important;
  color: var(--c-text-2) !important;
  border-radius: var(--r-pill) !important;
  font-weight: 400;
  line-height: 1.5;
}
.btn-ghost[disabled] { opacity: .55; }
/* ===== 点4 重色锚点：金额 / 关键数字的「三层锚」=====
   浅底 → 深玫瑰灰（正文级，可读但醒目）
   强调 → 珊瑚粉（按钮上、纯装饰数字）
   深底（on 珊瑚渐变）→ 纯白 + 暗边（靠 .text-on-dark 机制） */
.price          { color: var(--c-deep-rose); font-weight: 500; font-variant-numeric: tabular-nums; }
.price--hero    { color: var(--c-primary);    font-weight: 600; }   /* 珊瑚粉强调数字 */
.price--on-dark { color: #FFFFFF; text-shadow: var(--text-glow); font-weight: 600; } /* 深粉底上的白色金额 */
/* 数字统一等宽字间距，价格排布整齐、跨列对齐 */
.num { font-variant-numeric: tabular-nums; letter-spacing: .5rpx; }

.empty-state {
  text-align: center;
  padding: 80rpx 40rpx;
  color: var(--c-text-2);
  font-size: var(--t-md);
}
.empty-state .empty-title {
  display: block;
  font-size: var(--t-xl);
  font-weight: 500;
  color: var(--c-text);
  margin-top: 16rpx;
}
.empty-state .empty-hint {
  display: block;
  font-size: var(--t-sm);
  color: var(--c-text-3);
  margin-top: 12rpx;
}
.empty-state .emoji { font-size: 80rpx; margin-bottom: 16rpx; display: block; }
/* ===== 点6 空状态插图统一容器（治愈感）=====
   现状：各页空态直接把 <Icon size="120rpx"> 裸放在卡片里，白底 + 线描图标显得单薄。
   这里给一个「柔粉渐变底座 + 外光晕 + 上下呼吸」的容器：
     <view class="empty-illus"><Icon ... /></view>
   图标本身不必改尺寸，容器负责撑气场；纯 CSS，零图片资源。 */
.empty-illus {
  width: 200rpx; height: 200rpx;
  margin: 0 auto 20rpx;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  background: radial-gradient(circle at 34% 28%, #FFF1F4 0%, #FFE3E9 62%, #FFD8E1 100%);
  box-shadow: 0 12rpx 36rpx rgba(255,123,148,.22),
              0 1rpx 0 rgba(255,255,255,.95) inset;
  animation: illus-breath 3.2s ease-in-out infinite;
}
/* 底座上的柔光晕（让圆不再是「实心色块」） */
.empty-illus::after {
  content: '';
  position: absolute;
  width: 240rpx; height: 240rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255,123,148,.18) 0%, rgba(255,123,148,0) 68%);
  z-index: -1;
}
.empty-illus { position: relative; z-index: 0; }
@keyframes illus-breath {
  0%, 100% { transform: translateY(0) scale(1); }
  50%      { transform: translateY(-8rpx) scale(1.04); }
}
.empty-state .go-btn { /* 兼容旧 .go-btn 调用 */
  margin-top: 32rpx;
  font-size: var(--t-md);
}

.glass {
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
}

.tag-warn  { background: var(--c-pending-bg); color: var(--c-pending); }
.tag-green { background: #E3F7EE; color: var(--c-success); }
.tag-danger{ background: var(--c-danger-bg); color: var(--c-danger); }
.tag-gray  { background: rgba(255,240,243,.8); color: var(--c-text-2); }
.tag-chef  { background: var(--c-chef-light); color: var(--c-chef); }
/* 主色胶囊：浅薄荷渐变底 + 左字深玫瑰灰 + 右字珊瑚粉 */
.tag-pri {
  background: linear-gradient(135deg, #E6FFFD 0%, #DAFFFB 100%);
  border: 1rpx solid rgba(122,74,90,.18);
  color: var(--c-primary-dark);
}
.tag-pri .tag-num { color: var(--c-primary); font-weight: 500; }

/* 状态 pill（订单：待确认 / 已确认 / 已撤销） */
.st-0  { background: var(--c-pending-bg); color: var(--c-pending); }
.st-1  { background: #E3F7EE; color: var(--c-success); }
.st--1 { background: var(--c-danger-bg); color: var(--c-danger); }

/* 通用 button 文字行高修复 */
button { line-height: 1.5; white-space: nowrap; }
button::after { border: none; }

/* 小按钮 / 标签 pill 不让文字折行（百分比字号在窄容器里容易换行） */
.btn-primary, .btn-chef, .btn-danger, .btn-ghost,
.tag-pri, .tag-chef, .tag-warn, .tag-green, .tag-danger, .tag-gray,
.st-0, .st-1, .st--1,
.oc-status, .og-status, .ph-status, .dish-status, .cl-hint, .menu-badge,
.cart-badge, .fc-active, .oc-stat, .og-stat {
  white-space: nowrap;
}

/* 安全区：iOS 11.0-11.1 用 constant()，11.2+ 用 env()，双写降级 */
.safe-bottom {
  padding-bottom: constant(safe-area-inset-bottom);
  padding-bottom: env(safe-area-inset-bottom);
}
.safe-top {
  padding-top: constant(safe-area-inset-top);
  padding-top: env(safe-area-inset-top);
}

/* 按压反馈 */
.pressable { transition: transform .18s ease, opacity .18s ease, box-shadow .18s ease; }
.pressable:active {
  transform: scale(.96);
  opacity: .88;
  box-shadow: var(--glow-press);   /* 点2：按下时阴影收紧，模拟「压下去」 */
}

/* 动画状态 */
.anim-breath    { animation: sk-breath 2.6s ease-in-out infinite; }
.anim-heartbeat { animation: sk-heartbeat 1.6s ease-in-out infinite; }
.anim-shake     { animation: sk-shake .6s ease; }
.anim-fade-up   { animation: sk-fade-up .5s ease both; }
.anim-bounce-in { animation: sk-bounce-in .55s cubic-bezier(.34,1.56,.64,1) both; }
.anim-spin      { animation: sk-spin 1s linear infinite; }
.anim-slide-out-left { animation: sk-slide-out-left .38s ease both; }

/* 浮动购物车条由各页面自定义实现（index.vue 已有 .fab-cart；此前 .fab-cart-bar 为死代码） */

/* ============= 甜美少女粉 · 工具类（供页面消费） ============= */

/* --- 页面兜底背景：极浅薄荷微渐变（接近纯白），配 page{background:#FDF8F9} --- */
.page-bg-ethereal {
  background: var(--g-ethereal) !important;
  background-attachment: fixed;
  min-height: 100vh;
}
.page-bg-ethereal-soft {
  background: var(--g-ethereal-soft) !important;
  min-height: 100vh;
}
/* 粉色主题主背景类（新名，语义更直观；与 .page-bg-ethereal 等价） */
.page-bg-pink {
  background: var(--c-bg) !important;
  background-image: var(--g-ethereal) !important;
  background-attachment: fixed;
  min-height: 100vh;
}
/* 点1：弥散光光斑（写进三类的 background-image 末尾，显示在最上层）。
   合并写法是为了避开「!important 的 background-image 覆盖」，
   所以这里必须用同等权重并带 !important 才生效。 */
.page-bg-ethereal,
.page-bg-ethereal-soft,
.page-bg-pink {
  background-image:
    radial-gradient(circle at 8% 12%, rgba(255,123,148,.16) 0%, rgba(255,123,148,0) 42%),
    radial-gradient(circle at 92% 58%, rgba(255,168,188,.20) 0%, rgba(255,168,188,0) 46%),
    radial-gradient(circle at 18% 88%, rgba(218,255,251,.60) 0%, rgba(218,255,251,0) 48%),
    var(--g-ethereal) !important;
  background-repeat: no-repeat;
  background-attachment: fixed;
}

/* ============= 弥散光（点1 · 浅色区不再「一片平」，零 DOM）=============
   长页面滚过顶部渐变后只剩浅薄荷底，视觉会突然「塌」下去。
   方案：把 3 团极淡 radial-gradient 直接挂到「页面背景层」的 background-image 上 ——
   不进 DOM、不占 z-index、不挡点击，19 个用 .page-bg-* 的页面自动生效。
   （曾用绝对定位 <view class="blobs"> 实现，但 .page-bg-* 就是静态定位的页面根节点，
      z-index:0 的绝对层会压在其后插入的静态内容之上，卡片被蒙粉 —— 已改为本方案。）
   ⚠️ 与 --g-ethereal 的 linear-gradient 合并写在同一条 background-image 里，
      如果以后单独改 --g-ethereal，记得别把这几团 blob 覆盖掉。 */
/* ============= 顶部深粉渐变层（长列表滚动适配 · 核心方案）=============
   ⚠️ 绝对不要把深粉渐变铺满 100vh！
      铺满时页面底部的浅色区也变粉，白卡片 / 深色文字会一起糊掉。
   正确做法：页面根节点第一个子元素放
       <view class="hero-bg"></view>
   内容层保持 position:relative; z-index:1 才不会被盖住（hero-bg 是 z-index:0）。
   fixed 定位 → 列表怎么滚，背景都不动，不会因滚动长度在底部变形。
   高度固定 400rpx（规范值），底部用 mask 向下衰减到透明，与浅薄荷底自然衔接。 */
.hero-bg {
  position: fixed;
  top: 0; left: 0; right: 0;
  height: 400rpx;
  z-index: 0;
  pointer-events: none;
  background: var(--g-hero);           /* 珊瑚粉 → 薄荷白 */
  /* 向下衰减：不靠渐变硬收边，避免出现一条明显的分界线 */
  -webkit-mask-image: linear-gradient(180deg, #000 0%, rgba(0,0,0,.55) 55%, transparent 100%);
          mask-image: linear-gradient(180deg, #000 0%, rgba(0,0,0,.55) 55%, transparent 100%);
}
/* 需要更矮/更高的变体 */
.hero-bg--tall { height: 560rpx; }
.hero-bg--short { height: 300rpx; }

/* 顶部深粉区内容层：让文字浮在渐变之上 */
.hero-layer { position: relative; z-index: 1; }

/* 粉色同色系弥散光（绝不使用黑色投影）
   注：.glow-blue 为旧命名，为兼容既有页面保留，值已是浅粉柔光 */
.glow-pink  { box-shadow: var(--glow-pink) !important; }
.glow-blue  { box-shadow: var(--glow-blue) !important; }
.glow-soft  { box-shadow: var(--glow-soft) !important; }
.glow-deep  { box-shadow: var(--glow-deep) !important; }

/* ============= 毛玻璃卡片（亮色主题靠「透度 + 光晕」拉空间）=============
   ⚠️ 必须保留 background 半透明白：安卓部分机型 backdrop-filter 不生效时，
      靠这行 rgba 底色兜住可读性，否则卡片会变全透明、文字糊在渐变上。
   ⚠️ 透明度 0.6 是规范值，兼顾「透」和「亮色底上的文字对比度」。 */
/* 点2 玻璃感三件套：① 左上内部高光（最像玻璃的一笔）
                        ② 上沿亮线 inset
                        ③ 粉色外光晕
   点3 层级：L2 卡片用 .glass-card / .glass-card-2，L3 浮层用 .glass-card-3 + .layer-3 */
.glass-card {
  position: relative;
  background: rgba(255,255,255,0.62);              /* 安卓降级底色，勿删 */
  -webkit-backdrop-filter: blur(20rpx);
  backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.80);       /* 玻璃边缘亮色高光 */
  border-radius: var(--r-card);
  /* 外光晕 + 上沿亮线；两层缺一不可（只有外光晕会显得「贴」在背景上） */
  box-shadow: 0 8rpx 32rpx rgba(255,123,148,0.15),
              0 1rpx 0 rgba(255,255,255,0.95) inset;
}
/* 斜向高光：玻璃最明显的特征，用极淡白渐变压在卡片表层，pointer-events 不挡点击 */
.glass-card > .sheen,
.glass-card::after {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  border-radius: inherit;
  pointer-events: none;
  background: linear-gradient(135deg, rgba(255,255,255,.55) 0%, rgba(255,255,255,0) 42%);
}
.glass-card-2 {
  position: relative;
  background: rgba(255,255,255,0.78);              /* 降级底色，勿删（压住密集文字时用） */
  -webkit-backdrop-filter: blur(40rpx);
  backdrop-filter: blur(40rpx);
  border: 1rpx solid rgba(255,255,255,0.85);
  border-radius: var(--r-lg);
  box-shadow: 0 8rpx 32rpx rgba(255,123,148,0.15),
              0 1rpx 0 rgba(255,255,255,0.98) inset;
}
.glass-card-3 {
  background: rgba(255,255,255,0.42);              /* 降级底色，勿删（弱玻璃） */
  -webkit-backdrop-filter: blur(20rpx);
  backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.65);
  border-radius: var(--r-sm);
}

/* ============= 叠层预算（点3）=============
   规矩：同一页面最多三级，L1 < L2 < L3 必须肉眼可分。
   L1 = 页面背景（.blobs + .page-bg-*）
   L2 = 内容卡片（.layer-card）
   L3 = 浮层：弹窗 / 底部操作栏 / tab 悬浮胶囊（.layer-3） */
.layer-card {
  background: var(--l2-bg);
  border: var(--l2-border);
  border-radius: var(--r-card);
  box-shadow: var(--l2-shadow);
}
.layer-3 {
  background: var(--l3-bg);
  border: var(--l3-border);
  border-radius: var(--r-lg);
  box-shadow: var(--l3-shadow);
  -webkit-backdrop-filter: blur(40rpx);
  backdrop-filter: blur(40rpx);
}
/* 底部悬浮操作栏统一版式（既保证层级，也统一安全区与高度） */
.layer-3-bar {
  position: fixed;
  left: 0; right: 0; bottom: 0;
  z-index: 20;
  padding: 20rpx 5% calc(20rpx + constant(safe-area-inset-bottom));
  padding: 20rpx 5% calc(20rpx + env(safe-area-inset-bottom));
  background: rgba(255,255,255,0.92);
  -webkit-backdrop-filter: blur(40rpx);
  backdrop-filter: blur(40rpx);
  border-top: 1rpx solid rgba(255,255,255,0.9);
  box-shadow: 0 -8rpx 32rpx rgba(122,74,90,.10);
  border-radius: var(--r-lg) var(--r-lg) 0 0;
}

/* ============= 亮色主题文字色规范（避坑核心）=============
   顶部深粉区（约前 30% 屏）→ .text-on-dark；
   其余浅色区【绝对禁止白字】→ .text-on-light 系列。 */
/* 深粉渐变区：纯白 + 柔和粉色发光，保证白字在亮粉上仍清晰
   ⚠️ 关键细节：纯白 on #FF7B94 实测对比度仅 ≈2.47:1（低于大字号 3:1 门槛）。
      粉色光晕是同色系，只加「氛围」不增加边缘反差；因此这里叠了第二层
      「深玫瑰灰暗边」，用 0 位移 + 小模糊在字形外缘压出一圈深色轮廓，
      视觉上显著提升可读性，同时保持发光质感。两层的顺序不能颠倒。 */
.text-on-dark {
  color: #FFFFFF;
  text-shadow:
    0 1rpx 2rpx rgba(122,74,90,0.35),
    0 2rpx 10rpx rgba(255,123,148,0.5);
}
.text-on-dark-2 {
  color: rgba(255,255,255,0.92);
  text-shadow: 0 1rpx 2rpx rgba(122,74,90,0.30), 0 2rpx 10rpx rgba(255,123,148,0.45);
}
.text-on-dark-3 {
  color: rgba(255,255,255,0.78);
  text-shadow: 0 1rpx 2rpx rgba(122,74,90,0.25), 0 2rpx 8rpx rgba(255,123,148,0.40);
}
/* 浅色区：主标题深玫瑰灰 / 正文深灰 / 副标题半透明玫瑰 */
/* 浅色区（含薄荷白 #DAFFFB / 兜底 #FDF8F9 / 白卡）——实测对比度：
     #7A4A5A  7.11:1 ✓AA    （主标题）
     #333333 12.63:1 ✓AAA   （正文，规范「正文深灰」）
     副标题 .75 → 3.92:1     （规范给 0.60 只有 2.83:1，故上提；见 --c-text-2 注释）
     辅助   .62 → 3.06:1
   ⚠️ 本区块内一律禁止 #FFFFFF：纯白在 #DAFFFB 上只有 1.07:1，等于隐形。 */
.text-on-light     { color: #7A4A5A; }              /* 主标题 · 深玫瑰灰 */
.text-on-light-body{ color: #333333; }              /* 正文 · 正文深灰 */
.text-on-light-sub { color: var(--c-text-2); }      /* 副标题 · 半透明玫瑰 */
.text-on-light-hint{ color: var(--c-text-3); }      /* 辅助/占位 */

/* 兼容旧类名（等义） */
.t-glass      { color: var(--c-on-glass); }
.t-glass-2    { color: var(--c-on-glass-2); }
.t-glass-3    { color: var(--c-on-glass-3); }
.t-deep-rose  { color: var(--c-deep-rose); }
.t-deep-blue  { color: var(--c-deep-blue); }

/* ============= 胶囊标签（Pill Tag） =============
   结构：<view class="pill"><text class="pill-label">已点</text><text class="pill-num">3 道</text></view>
   左半薄荷白承载语义，右半珊瑚粉承载数值。 */
.pill {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 6rpx 20rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #EDFFFF 0%, #DAFFFB 100%);
  border: 1rpx solid rgba(255,123,148,0.22);
  box-shadow: 0 2rpx 10rpx rgba(255,123,148,0.12);
  white-space: nowrap;
}
.pill .pill-label {
  font-size: var(--t-xs);
  font-weight: 400;
  color: #7A4A5A;              /* 浅底 → 深玫瑰灰字 */
  letter-spacing: 1rpx;
}
.pill .pill-num {
  font-size: var(--t-xs);
  font-weight: 500;
  /* 点9：珊瑚粉 #FF7B94 作小字在浅薄荷底上仅 2.31:1 —— 数字是要抓眼的锚点，
     不能当装饰色用。改用深珊瑚 #FF5D7E（2.76:1）+ 字重 600 提升辨识。 */
  color: var(--c-primary-dark);              /* 数值 → 珊瑚粉（重色锚点） */
  letter-spacing: 1rpx;
}
/* 镜像渐变胶囊（规范）：底为 linear-gradient(90deg, #DAFFFB 0%, #FF7B94 100%)
   ⚠️ 「左浅右深」是硬要求：左段是薄荷浅底 → 字用深玫瑰灰；右段是珊瑚深底 → 字用纯白。
      反过来（左深右浅）右段白字会落在浅底上，直接不可读。
   ⚠️ 右侧数值额外套一个深粉实底，作为渐变不生效（降级/静态导出）时的兜底。 */
.pill-mirror {
  background: linear-gradient(90deg, #DAFFFB 0%, #FF7B94 100%);
  border: 1rpx solid rgba(255,255,255,0.8);
  box-shadow: 0 4rpx 16rpx rgba(255,123,148,0.22);
  padding: 6rpx 8rpx 6rpx 20rpx;
}
.pill-mirror .pill-label {
  color: #7A4A5A;              /* 左段：薄荷浅底 → 深玫瑰灰字 */
}
.pill-mirror .pill-num {
  color: #FFFFFF;              /* 右段：珊瑚深底 → 纯白字 */
  background: linear-gradient(135deg, #FF7B94 0%, #FF5D7E 100%);
  border-radius: 999rpx;
  padding: 4rpx 18rpx;
  /* 白字 on 珊瑚粉 → 暗边 + 粉晕双层（同 .text-on-dark 的理由） */
  text-shadow: 0 1rpx 2rpx rgba(122,74,90,0.35), 0 2rpx 10rpx rgba(255,123,148,0.5);
}
/* 深粉实底胶囊（用于深色强调位） */
.pill-solid {
  background: var(--g-primary);
  border: none;
  box-shadow: var(--glow-pink);
}
.pill-solid .pill-label { color: #FFFFFF; }
.pill-solid .pill-num   { color: rgba(255,255,255,0.92); }

/* 字重：纤细、轻盈（避免 700 那种厚重视觉） */
.w-thin { font-weight: 300; }
.w-light { font-weight: 400; }
.w-md { font-weight: 500; }

/* 大圆角 */
.r-glass-lg { border-radius: 40rpx !important; }
.r-glass-md { border-radius: 32rpx !important; }
.r-glass-sm { border-radius: 24rpx !important; }

/* ============= 星光点缀（CSS 画四角星，不依赖图片）=============
   用法：
     <view class="stars"><view class="star star--spark" style="left:12%;top:8%;--d:0s"></view></view>
   ⚠️ 白星只在顶部深粉段可见；浅色段上必须换 .star--deep（珊瑚粉星），
      否则白星落在 #DAFFFB 上等于隐形。
   四角星 = 两个各自 rotate 45°/135° 的细长渐变条交叉，纯伪元素，零图片零请求。 */
.stars {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
  z-index: 0;
}
/* ⚠️ 必用变体：stars 的 inset:0 是相对「最近的定位祖先」，
   若祖先就是页面根节点（position:relative, 高度=整页内容高），
   星星的 top:6% 会按整页高度算 —— 长列表上星星全被推到浅色段，
   白星落在 #DAFFFB 上等于隐形。
   .stars--hero 把容器高度钉在顶部渐变层的范围内（560rpx ≥ .hero-bg 的 400rpx），
   这样 top 百分比才对应「顶部深粉区」。 */
.stars--hero {
  bottom: auto;
  height: 560rpx;
}
.star {
  position: absolute;
  width: 6rpx; height: 6rpx;
  border-radius: 50%;
  background: #FFFFFF;
  box-shadow: 0 0 12rpx 2rpx rgba(255,255,255,0.85);
  animation: twinkle 3.2s ease-in-out infinite;
  animation-delay: var(--d, 0s);
  opacity: 0;
}
/* 四角星：同位置叠两个交叉的细长渐变条 */
.star--spark { width: 20rpx; height: 20rpx; background: none; box-shadow: none; }
.star--spark::before,
.star--spark::after {
  content: '';
  position: absolute;
  left: 50%; top: 50%;
  width: 2rpx; height: 20rpx;
  margin: -10rpx 0 0 -1rpx;
  border-radius: 999rpx;
  background: linear-gradient(180deg, transparent 0%, #FFFFFF 50%, transparent 100%);
  animation: twinkle 3.6s ease-in-out infinite;
  animation-delay: var(--d, 0s);
}
.star--spark::after { transform: rotate(90deg); }
/* 浅色段里的星星改珊瑚粉，白星在这里是隐形的 */
.star--deep {
  background: #FF7B94;
  box-shadow: 0 0 12rpx 2rpx rgba(255,123,148,0.55);
}
.star--deep.star--spark::before,
.star--deep.star--spark::after {
  background: linear-gradient(180deg, transparent 0%, #FF7B94 50%, transparent 100%);
}
/* 小尺寸变体，缩小后 6rpx 会小于 1px 被截断，用 transform 等比缩 */
.star--sm { transform: scale(.66); }
.star--lg { transform: scale(1.5); }

/* 注：旧版 .custom-nav* 系列样式已删除，由 components/CustomNav.vue 接管
   （getMenuButtonBoundingClientRect 动态算胶囊避让，比硬编码 88rpx + margin-right 平衡更可靠） */
</style>