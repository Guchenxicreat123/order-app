<template>
  <view class="welcome-page" :class="{ 'leaving': leaving }">
    <!-- 背景光晕 -->
    <view class="halo halo-1"></view>
    <view class="halo halo-2"></view>

    <!-- 中央 logo 区 -->
    <view class="logo-wrap">
      <view class="spoon-ring">
        <image
          src="/static/icons/spoon.svg"
          class="logo-spoon anim-bounce-in"
          mode="aspectFit"
        />
        <image
          src="/static/icons/heart-gold.svg"
          class="logo-heart anim-bounce-in"
          mode="aspectFit"
        />
      </view>

      <view class="brand anim-bounce-in" style="animation-delay: .15s">
        <text class="brand-name">勺意</text>
        <text class="brand-en">Shao Yi</text>
      </view>

      <text class="slogan anim-bounce-in" style="animation-delay: .3s">
        点菜是因，做饭是所以
      </text>
      <text class="sub-slogan anim-bounce-in" style="animation-delay: .4s">
        勺子里盛着的，从来不只是菜
      </text>
    </view>

    <!-- 底部小动效：动画完成即渐隐 -->
    <view class="bottom-deco" :class="{ 'fade-out': !showFooter }">
      <image src="/static/icons/sparkle.svg" class="deco-icon" mode="aspectFit" />
      <text class="deco-text">{{ footer }}</text>
      <image src="/static/icons/sparkle.svg" class="deco-icon" mode="aspectFit" />
    </view>

    <!-- 首次打开/未登录时的登录入口：必须由用户点击才发起登录 -->
    <!-- 必须用 v-if：v-show 编译成 hidden 属性，会被 .login-btn-wrap 里的 display:flex 覆盖掉，
         导致按钮无论 showLoginBtn 真假都一直显示。v-if 是节点级增删，不受 CSS 影响。 -->
    <view v-if="showLoginBtn" class="login-btn-wrap anim-fade-up">
      <button class="login-btn" :disabled="loggingIn" @tap="onTapLogin">
        {{ loggingIn ? '登录中…' : '立即登录' }}
      </button>
      <text class="login-hint">首次使用需要登录 · 登录即注册</text>
    </view>

    <!-- loading 勺子 -->
    <view v-if="loading" class="boot-loader">
      <SkeletonBlock variant="spoon" height="120rpx" width="220rpx" text="掌勺中…" />
    </view>
  </view>
</template>


<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import {
  getToken, forceLogin, onAuthLogin, checkUserExists,
} from '@/utils/request.js'

// 开机动画时序
const MIN_BOOT_MS = 3000   // 至少播 3s，让用户看清 logo
const MAX_BOOT_MS = 8000   // 兜底上限：无论如何都要把登录入口露出来（防止卡在 loading）

const loading = ref(true)       // 「掌勺中…」loading
const showFooter = ref(true)    // 底部小动效
const leaving = ref(false)
const footer = ref('正在为你打开温暖的小家')
const showLoginBtn = ref(false) // 「立即登录」按钮
const loggingIn = ref(false)
const loginStatus = ref('auto') // auto | ok | timeout

let offAuth = null
let jumped = false     // 防止多个路径同时 reLaunch
let bootStart = 0
let showLoginTimer = 0
let minBootTimer = 0

function sleep(ms) { return new Promise(r => setTimeout(r, ms)) }

/** 登录后跳转：避免重复 reLaunch；一律进首页（没有家庭也先进主画面浏览） */
async function gotoAfterLogin() {
  if (jumped) return
  // 登录成功后，至少让用户看满 MIN_BOOT_MS 开机动画
  const elapsed = Date.now() - bootStart
  const remain = Math.max(0, MIN_BOOT_MS - elapsed)
  if (remain > 0) await sleep(remain)
  if (jumped) return
  jumped = true
  loading.value = false
  try {
    // 不再拦截：没有家庭也先进主画面浏览，
    // 等用户真正点菜时再由首页入口提示创建/加入家庭（我的页也有入口）。
    uni.reLaunch({ url: '/pages/index/index' })
  } catch (e) {
    uni.reLaunch({ url: '/pages/index/index' })
  }
}

/** 用户点「立即登录」且登录成功 → 走"至少 3s 开机动画" 再进画面 */
offAuth = onAuthLogin(() => { gotoAfterLogin() })

/** 展示登录入口（未登录时开机动画播完就展示；也有兜底超时） */
function showLoginEntry(reason) {
  loginStatus.value = reason === 'ok' ? 'ok' : 'need'
  showLoginBtn.value = true
  loading.value = false
  showFooter.value = false
}
onMounted(async () => {
  bootStart = Date.now()

  // 1) 顶部小动效：到 MIN_BOOT_MS 渐隐
  minBootTimer = setTimeout(() => {
    showFooter.value = false
  }, MIN_BOOT_MS)

  // 2) 已有 token（本次会话还有效）→ 直接进小程序，不用问后端
  if (getToken()) {
    await gotoAfterLogin()
    return
  }

  // 3) 判断「库里是否已经存在这个用户」——只查，不写库。
  //    用户主动退出过（logout_by_user）时不再自动进入，必须重新点登录。
  const loggedOutByUser = (() => {
    try { return !!uni.getStorageSync('logout_by_user') } catch (e) { return false }
  })()
  if (!loggedOutByUser) {
    try {
      const st = await checkUserExists()
      if (st && st.exists) {
        // 老用户：静默登录（不会新建用户，因为库里已经有）后直接进入
        const { promise } = forceLogin()
        const ok = await promise
        if (ok) {
          await gotoAfterLogin()
          return
        }
      }
    } catch (e) {
      // 查不到（网络异常 / code 失效等）→ 当作需要用户手动登录处理
    }
  }

  // 4) 库里没有这个用户（或不点登录就不该入库）→ 展示「立即登录」，等用户点击；
  //    用户不点就退出小程序，数据库里不会留下任何记录。
  //    出现时机对齐开机动画：动画播完就露出来。
  const waitBoot = Math.max(0, MIN_BOOT_MS - (Date.now() - bootStart))
  setTimeout(() => {
    if (jumped) return
    showLoginEntry('need')
  }, waitBoot)
  showLoginTimer = setTimeout(() => {
    if (jumped) return
    showLoginEntry('timeout')
  }, MAX_BOOT_MS)
})

onUnmounted(() => {
  try { offAuth && offAuth() } catch (e) {}
  try { showLoginTimer && clearTimeout(showLoginTimer) } catch (e) {}
  try { minBootTimer && clearTimeout(minBootTimer) } catch (e) {}
})

async function onTapLogin() {
  if (loggingIn.value) return
  loggingIn.value = true
  loading.value = true
  try {
    // 用户主动点击时，如果后台 forceLogin 已经在跑，复用同一个 promise
    const { promise } = forceLogin({ showLoading: true })
    const ok = await promise
    if (!ok) {
      uni.showToast({ title: '网络异常，请稍后再试', icon: 'none' })
    }
    // 成功则由 onAuthLogin 监听器负责跳转
  } finally {
    loggingIn.value = false
  }
}
</script>

<style scoped>
/* ============= 第一版启动页（仙女粉）· 原样还原 =============
   背景整屏粉色渐变 #FF5CA8 → #FF85C2，文字纯白 + 黑色投影，
   金色光晕、纯白胶囊登录按钮。刻意保留 v1 的原始观感（含深字权重 800 /
   黑色投影），不做理论上的对比度优化，仅保证在小程序里可正常编译运行。 */
.welcome-page {
  position: relative;
  min-height: 100vh;
  background: linear-gradient(135deg,#FF5CA8,#FF85C2);  /* v1 整屏仙女粉（不用 var，防被当前珊瑚粉 --g-primary 覆盖） */
  display: flex; flex-direction: column;
  align-items: center; justify-content: space-between;
  padding: 20% 0 16%;
  overflow: hidden;
  box-sizing: border-box;
  transition: opacity .28s ease, transform .28s ease;
}
.welcome-page.leaving {
  opacity: 0;
  transform: scale(1.04);
}

.halo {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}
.halo-1 {
  width: 520rpx; height: 520rpx;
  background: radial-gradient(circle, rgba(255,255,255,.35) 0%, rgba(255,255,255,0) 70%);
  top: -20%; left: -16%;
  animation: halo-float 4s ease-in-out infinite;
}
.halo-2 {
  width: 360rpx; height: 360rpx;
  background: radial-gradient(circle, rgba(255,201,60,.35) 0%, rgba(255,201,60,0) 70%);
  bottom: -10%; right: -10%;
  animation: halo-float 5s ease-in-out infinite reverse;
}
@keyframes halo-float {
  0%, 100% { transform: translate(0,0) scale(1); }
  50%      { transform: translate(20rpx,-30rpx) scale(1.06); }
}

.logo-wrap {
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 4%; flex: 1;
}

.spoon-ring {
  position: relative;
  width: 220rpx; height: 220rpx;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255,255,255,0.18);
  border-radius: 50%;
  box-shadow: 0 16rpx 48rpx rgba(0,0,0,0.15);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
}
.logo-spoon {
  width: 160rpx; height: 160rpx;
}
/* C8：心形往右下挪一些、不挡勺柄；同时缩小一点 */
.logo-heart {
  position: absolute;
  bottom: 24rpx; right: 24rpx;
  width: 48rpx; height: 48rpx;
  animation-delay: .25s;
}

.brand {
  display: flex; flex-direction: column;
  align-items: center; gap: 1%;
}
.brand-name {
  font-size: 262.5%; font-weight: 800;
  color: white;
  letter-spacing: 8rpx;
  text-shadow: 0 4rpx 12rpx rgba(0,0,0,0.12);
}
.brand-en {
  font-size: var(--t-xs); color: rgba(255,255,255,.85);
  letter-spacing: 6rpx;
}

.slogan {
  font-size: 93.75%; color: white;
  letter-spacing: 4rpx;
  opacity: 0.96;
}
.sub-slogan {
  font-size: var(--t-xs); color: rgba(255,255,255,.82);
  letter-spacing: 2rpx;
  margin-top: 1%;
}

/* D6：底部文字加载完成后淡出 */
.bottom-deco {
  display: flex; align-items: center; gap: 2%;
  color: rgba(255,255,255,.85);
  font-size: var(--t-xs);
  transition: opacity .35s ease, transform .35s ease;
}
.bottom-deco.fade-out {
  opacity: 0;
  transform: translateY(20rpx);
  pointer-events: none;
}
.deco-icon { width: 28rpx; height: 28rpx; opacity: .85; }

.boot-loader { margin-bottom: 8%; }
/* 「掌勺中…」文字：组件默认用深玫瑰灰 var(--c-text-2)，落在整屏粉底上只有 2.49:1，
   几乎看不见（v1 原值 #9B7E8A 更差，只有 1.28:1）。这里在启动页内单独改成白色字，
   只作用于本页，不影响其他页面复用 SkeletonBlock。 */
.boot-loader >>> .sk-spoon-text {
  color: #FFFFFF;
  text-shadow: 0 1rpx 3rpx rgba(155,40,90,.45);
}

/* 首次进入的「立即登录」按钮 */
.login-btn-wrap {
  display: flex; flex-direction: column; align-items: center;
  width: 80%;
  margin: 6% auto 4%;
}
.login-btn-wrap >>> .login-btn {
  width: 100%;
  height: 96rpx;
  border-radius: 999rpx !important;
  background: white !important;
  color: #FF5CA8 !important;  /* v1 仙女粉字色 */
  font-size: 93.75% !important;
  font-weight: 700 !important;
  letter-spacing: 6rpx !important;
  box-shadow: 0 8rpx 24rpx rgba(0,0,0,0.18);
  /* 真正居中：用 flex + line-height:1 顶替原生 button 的 inline-block 默认行为 */
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  line-height: 1 !important;
  /* 抹掉小程序原生 button 的内边距，避免文字被推到一侧 */
  padding: 0 !important;
  margin: 0 !important;
  /* 抹掉原生 button 的默认边框（部分基础库会画 1px 边） */
  border: 0 !important;
  /* 抹掉 hover / active 时微信基础库附加的半透明蒙层 */
  position: relative;
}
.login-btn-wrap >>> .login-btn::after {
  border: 0 !important;
}
.login-btn[disabled] { opacity: .7; color: #FF5CA8 !important; }
.login-hint {
  margin-top: 16rpx;
  font-size: 68.75%;
  color: rgba(255,255,255,0.85);
  letter-spacing: 2rpx;
}
</style>
