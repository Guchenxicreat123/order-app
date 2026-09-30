<template>
  <view class="custom-nav" :class="`gradient-${tone}`" :style="navStyle">
    <view class="custom-nav-inner">
      <view v-if="showBack" class="custom-nav-back pressable" @tap="onBack">
        <Icon name="back" size="28rpx" tone="white" />
      </view>
      <text class="custom-nav-title">{{ title }}</text>
      <view class="custom-nav-right" :style="rightStyle">
        <slot name="right"></slot>
      </view>
    </view>
  </view>
</template>

<script setup>
import Icon from './Icon.vue'
import { ref, computed } from 'vue'

const props = defineProps({
  title: { type: String, default: '' },
  tone:  { type: String, default: 'pri' }, // pri | chef | glass
  showBack: { type: Boolean, default: true },
})
const emit = defineEmits(['back'])
const onBack = () => {
  const pages = getCurrentPages()
  if (pages.length > 1) uni.navigateBack({ delta: 1 })
  else uni.switchTab({ url: '/pages/index/index' })
  emit('back')
}

// 微信小程序自定义导航：JS 计算状态栏 + 胶囊位置（可靠方案）
// rpx 换算基准：750rpx = 屏宽 px
let winW = 375
let statusH = 20
let menuTop = 0    // 胶囊距屏幕顶部
let menuH = 32     // 胶囊高
let menuRight = 0  // 胶囊右边到屏幕左边缘（px）
let menuW = 87     // 胶囊宽（px），默认 87
try {
  const sys = uni.getSystemInfoSync()
  winW = sys.windowWidth || 375
  statusH = sys.statusBarHeight || 20
} catch (e) {}
try {
  const m = uni.getMenuButtonBoundingClientRect()
  if (m && m.height) {
    menuTop = m.top; menuH = m.height
    menuRight = m.right; menuW = m.width || menuW
  }
} catch (e) {}

const px2rpx = (px) => Math.round(px * 750 / winW)

// 导航栏总高 = 状态栏 + 胶囊对齐行
const navBarRpx = computed(() => {
  if (menuTop > 0 && menuH > 0) {
    const gap = menuTop - statusH
    const innerH = menuH + gap * 2
    return px2rpx(statusH + innerH)
  }
  return px2rpx(statusH) + 88 // 兜底
})
// 状态栏占位高度
const statusBarRpx = computed(() => px2rpx(statusH))
// 右侧内容/胶囊避让：right slot 终止在胶囊左缘再偏左 12px（原误用右缘距，现用胶囊左缘 = menuRight - menuW）
const rightPadRpx = computed(() => {
  if (menuRight > 0) {
    const capsuleLeftFromRight = winW - (menuRight - menuW) // 胶囊左缘到屏右距离
    return px2rpx(capsuleLeftFromRight + 12)
  }
  return px2rpx(87 + 12) // 兜底
})
const navStyle = computed(() => ({
  paddingTop: statusBarRpx.value + 'rpx',
  height: navBarRpx.value + 'rpx'
}))
const rightStyle = computed(() => ({
  right: rightPadRpx.value + 'rpx'
}))
</script>

<style scoped>
/* 注：custom-nav 系列样式已搬到组件内部，且修复了：
   1) App.vue 残留 .custom-nav-title{margin-right:68rpx} 会让标题左偏 → 这里显式 margin-right:0
   2) .custom-nav 是 relative，整页滚动页面（如 shopping-list）滚动时导航会一起滚走 → 改 sticky
   3) 自包含 .pressable，避免组件样式隔离下失效 */
.custom-nav {
  position: sticky;
  top: 0;
  left: 0;
  right: 0;
  z-index: 200;
  width: 100%;
  box-sizing: border-box;
}
.custom-nav.gradient-pri  { background: var(--g-primary); }
.custom-nav.gradient-chef { background: var(--g-chef); }
/* 实色粉导航上的标题与返回图标：白字必须带暗边（见 App.vue .text-on-dark） */
.custom-nav.gradient-pri  .custom-nav-title,
.custom-nav.gradient-chef .custom-nav-title {
  color: #FFFFFF;
  text-shadow: 0 1rpx 2rpx rgba(122,74,90,0.35), 0 2rpx 10rpx rgba(255,123,148,0.5);
}
/* 甜美少女粉·透明导航（让下方渐变透上来），标题用深玫瑰灰 */
.custom-nav.gradient-glass {
  background: transparent;
}

.custom-nav-inner {
  position: relative;
  width: 100%;
  height: 100%;
  box-sizing: border-box;
}

/* 返回按钮：绝对定位在左侧，不参与标题居中 */
.custom-nav-back {
  position: absolute;
  left: 16rpx;
  top: 50%;
  transform: translateY(-50%);
  width: 56rpx; height: 56rpx;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255,255,255,0.28);
  border: 1rpx solid rgba(255,255,255,0.4);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border-radius: 50%;
}

/* 标题：绝对居中于整个导航宽度（视觉正中，避免左右留白不对称造成偏移）
   注：glass 态标题浮在浅薄荷渐变上，纯白看不清，改用深玫瑰灰 */
.custom-nav-title {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  margin: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--t-xl);
  font-weight: 400;
  letter-spacing: 2rpx;
  /* glass 态标题浮在浅粉渐变上 → 深玫瑰灰（浅底禁白字）。
     pri / chef 态标题落在实色珊瑚粉上 → 由 .gradient-pri/.gradient-chef 覆盖为白 + 暗边。 */
  color: var(--c-text);
  text-shadow: 0 4rpx 16rpx rgba(255,123,148,.22);
  text-align: center;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  padding: 0 8%;
  box-sizing: border-box;
  pointer-events: none;
}

/* 右侧插槽：定位在胶囊左侧，不参与标题居中 */
.custom-nav-right {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  min-width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

/* 自包含：组件样式隔离下全局 .pressable 失效 */
.pressable { transition: transform .18s ease, opacity .18s ease; }
.pressable:active { transform: scale(.96); opacity: .85; }
</style>