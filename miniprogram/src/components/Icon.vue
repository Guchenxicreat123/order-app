<template>
  <view
    class="icon"
    :class="{ 'icon-anim': anim }"
    :style="{ width: sizeRpx, height: sizeRpx }"
  >
    <image
      :src="src"
      class="icon-img"
      :style="{ width: sizeRpx, height: sizeRpx }"
      mode="aspectFit"
    />
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 图标名（对应 static/icons/<name>.svg） */
  name: { type: String, required: true },
  /** 尺寸（任意 CSS 值，如 "48rpx"、"32rpx"） */
  size: { type: [String, Number], default: '48rpx' },
  /** 颜色风格：primary(粉) | white | chef(蓝) | gold | dark(深色，用于浅薄荷底卡片) */
  tone: { type: String, default: 'primary' },
  /** 是否启用轻量入场动画 */
  anim: { type: Boolean, default: false },
})

const sizeRpx = computed(() => {
  if (typeof props.size === 'number') return props.size + 'rpx'
  return props.size
})

// 存在 -white 变体的图标（与 static/icons/ 目录保持同步；未列出的回落主色文件）
const WHITE_VARIANTS = new Set([
  'back', 'cart', 'check', 'close', 'cross', 'plus', 'spoon', 'minus',
  'home', 'flower', 'search', 'heart-egg',
  'edit', 'family', 'copy'
])
// 存在 -gold 变体的图标
const GOLD_VARIANTS = new Set(['heart'])
// 存在 -dark 变体的图标（深色，用在浅薄荷底卡片）
const DARK_VARIANTS = new Set(['heart-egg'])
// 本身已是蓝色设计的图标，chef tone 直接用原文件
const CHEF_VARIANTS = new Set(['chef-hat'])

const src = computed(() => {
  const name = props.name
  const dir = '/static/icons/'
  if (props.tone === 'white') {
    return dir + (WHITE_VARIANTS.has(name) ? name + '-white.svg' : name + '.svg')
  }
  if (props.tone === 'dark') {
    return dir + (DARK_VARIANTS.has(name) ? name + '-dark.svg' : name + '.svg')
  }
  if (props.tone === 'chef') {
    return dir + name + '.svg'
  }
  if (props.tone === 'gold') {
    return dir + (GOLD_VARIANTS.has(name) ? name + '-gold.svg' : name + '.svg')
  }
  return dir + name + '.svg'
})
</script>

<style scoped>
.icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.icon-img {
  display: block;
}
.icon-anim .icon-img {
  animation: icon-in 420ms cubic-bezier(.34,1.56,.64,1) both;
}
@keyframes icon-in {
  0%   { transform: scale(.6); opacity: 0; }
  60%  { transform: scale(1.15); opacity: 1; }
  100% { transform: scale(1); }
}
</style>
