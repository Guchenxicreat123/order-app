<template>
  <view v-if="variant === 'spoon'" class="sk-spoon" :style="{ width: width, height: height }">
    <view class="sk-spoon-bubbles">
      <view class="sk-bubble b1"></view>
      <view class="sk-bubble b2"></view>
      <view class="sk-bubble b3"></view>
    </view>
    <image src="/static/icons/spoon.svg" class="sk-spoon-icon anim-swing" mode="aspectFit" />
    <text v-if="text" class="sk-spoon-text">{{ text }}</text>
  </view>
  <view
    v-else
    class="skeleton"
    :class="['sk-' + variant]"
    :style="{ height: height, width: width }"
  />
</template>

<script setup>
defineProps({
  variant: { type: String, default: 'block' }, // block | line | card | circle | spoon
  height:  { type: String, default: '32rpx' },
  width:   { type: String, default: '100%' },
  text:    { type: String, default: '' },
})
</script>

<style scoped>
.skeleton {
  background: linear-gradient(90deg, rgba(255,255,255,0.35) 25%, rgba(255,255,255,0.7) 50%, rgba(255,255,255,0.35) 75%);
  background-size: 200% 100%;
  animation: sk-shimmer 1.4s ease-in-out infinite;
  border-radius: 16rpx;
}
.sk-circle { border-radius: 50%; }
.sk-card   { border-radius: 32rpx; }

/* 勺子摆动 loading */
.sk-spoon {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 2%;
  padding: 2%;
}
.sk-spoon-icon {
  width: 72rpx; height: 72rpx;
  transform-origin: 50% 90%;
}
.anim-swing { animation: sk-spoon-swing 1.2s ease-in-out infinite; }
.sk-spoon-text { font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); letter-spacing: 2rpx; }

.sk-spoon-bubbles { position: relative; width: 64rpx; height: 0; }
.sk-bubble {
  position: absolute; left: 0; top: 0;
  width: 14rpx; height: 14rpx; border-radius: 50%;
  background: rgba(255,255,255,0.8);
  border: 2rpx solid var(--c-primary, #FF7B94);
  animation: sk-bubble 1.8s ease-out infinite;
  opacity: 0;
}
.b1 { left: 0rpx;  animation-delay: 0s; }
.b2 { left: 22rpx; animation-delay: .5s; }
.b3 { left: 44rpx; animation-delay: 1s; }
</style>