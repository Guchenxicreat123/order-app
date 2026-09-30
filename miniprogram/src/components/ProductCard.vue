<template>
  <view class="product pressable" @tap="$emit('view', dish.id)">
    <view class="p-img-wrap">
      <text class="p-emoji">{{ dish.imageEmoji || '🍽️' }}</text>
    </view>
    <view class="p-info">
      <text class="p-name">{{ dish.name }}</text>
      <text class="p-desc" v-if="dish.description">{{ dish.description }}</text>
      <view class="p-meta">
        <text class="p-price">¥{{ dish.price }}</text>
        <text class="p-unit" v-if="dish.spiceLevel === 1">微辣</text>
        <text class="p-unit" v-else-if="dish.spiceLevel === 2">重辣</text>
      </view>
    </view>
    <view class="add-btn pressable" @tap.stop="$emit('add', dish)">
      <Icon name="plus" size="28rpx" tone="white" />
    </view>
  </view>
</template>

<script setup>
import Icon from './Icon.vue'

defineProps({
  dish: { type: Object, required: true }
})
defineEmits(['view', 'add'])
</script>

<style scoped>
/* 全百分比布局：gap/padding/margin 用 %；保留 rpx 给图标/阴影/圆角/border */
.product {
  display: flex; align-items: center; gap: 3%;
  padding: 3% 4%;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  border-radius: 32rpx;
  margin-bottom: 16rpx;
  box-shadow: var(--glow-soft);
}
.p-img-wrap {
  width: 80rpx; height: 80rpx;
  background: rgba(255,255,255,0.7);
  border: 2rpx solid rgba(255,255,255,0.55);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.p-emoji { font-size: 137.5%; line-height: 1; }
.p-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1%; }
.p-name { font-size: 93.75%; font-weight: 400; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.p-desc { font-size: 75%; font-weight: 300; color: var(--c-text-2); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.p-meta { display: flex; align-items: center; gap: 2%; margin-top: 1%; }
.p-price { font-size: 100%; font-weight: 500; color: var(--c-deep-rose); }
.p-unit { font-size: 68.75%; font-weight: 300; color: var(--c-pending); }
.add-btn {
  width: 56rpx; height: 56rpx; border-radius: 50%;
  background: var(--g-primary);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
</style>