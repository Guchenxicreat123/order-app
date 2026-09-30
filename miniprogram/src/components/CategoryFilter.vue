<template>
  <view class="left">
    <view
      v-for="cat in categories"
      :key="cat.id"
      class="cat-item"
      :class="{ active: activeId === cat.id }"
      @tap="$emit('select', cat.id)"
    >
      <text class="cat-emoji">{{ cat.emoji }}</text>
      <text class="cat-name">{{ cat.name }}</text>
    </view>
  </view>
</template>

<script setup>
defineProps({
  categories: { type: Array, default: () => [] },
  activeId: { type: [Number, String], default: null }
})
defineEmits(['select'])
</script>

<style scoped>
/* 组件根 .left 与页面 .cat-side 定宽一致(120rpx)，宿主 + 内部双保险，杜绝中间空隙 */
.left {
  width: 120rpx;
  flex-shrink: 0;
  box-sizing: border-box;
  background: rgba(255,255,255,0.4);
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 8rpx 0;
  overflow-y: auto;
}
.cat-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  height: 90rpx;
  padding: 0 4rpx;
  position: relative;
  color: var(--c-text-2);
  font-weight: 300;
}
.cat-emoji {
  font-size: 34rpx;
  flex-shrink: 0;
  line-height: 1;
  margin-right: 6rpx;
}
.cat-name {
  font-size: 26rpx;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cat-item.active {
  background: rgba(255,255,255,0.7);
  color: var(--c-deep-rose);
  font-weight: 400;
}
.cat-item.active .cat-name { color: var(--c-deep-rose); }
.cat-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 6rpx;
  height: 40rpx;
  background: var(--c-primary);
  border-radius: 0 4rpx 4rpx 0;
}
</style>
