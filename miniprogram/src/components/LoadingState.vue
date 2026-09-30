<template>
  <view class="ls-wrap" :class="{ full }">
    <!-- inline: 单行小提示 -->
    <view v-if="variant === 'inline'" class="ls-inline anim-breath">
      <view class="ls-dot" />
      <text class="ls-text">{{ text }}</text>
    </view>

    <!-- bar: 列表骨架 -->
    <view v-else-if="variant === 'bar'" class="ls-bars">
      <SkeletonBlock
        v-for="i in barCount"
        :key="i"
        variant="line"
        :height="barHeight"
        :width="i === barCount ? '60%' : '100%'"
      />
    </view>

    <!-- card: 卡片骨架 -->
    <view v-else-if="variant === 'card'" class="ls-cards">
      <SkeletonBlock
        v-for="i in cardCount"
        :key="i"
        variant="card"
        :height="cardHeight"
      />
    </view>

    <!-- spoon: 勺子摆动 loading -->
    <view v-else-if="variant === 'spoon'" class="ls-spoon">
      <SkeletonBlock
        variant="spoon"
        :height="spoonHeight"
        :width="spoonWidth"
        :text="text"
      />
    </view>

    <!-- empty: 空态 -->
    <view v-else-if="variant === 'empty'" class="ls-empty anim-fade-up">
      <Icon :name="icon" size="100rpx" tone="primary" class="empty-icon anim-breath" />
      <text class="empty-title">{{ title || '空空如也' }}</text>
      <text class="empty-hint" v-if="hint">{{ hint }}</text>
    </view>
  </view>
</template>

<script setup>
import Icon from './Icon.vue'
import SkeletonBlock from './SkeletonBlock.vue'

defineProps({
  variant: { type: String, default: 'bar' }, // inline | bar | card | spoon | empty
  text:    { type: String, default: '加载中…' },
  full:    { type: Boolean, default: false },
  icon:    { type: String, default: 'heart-egg' },
  title:   { type: String, default: '' },
  hint:    { type: String, default: '' },
  barCount:    { type: Number, default: 3 },
  barHeight:   { type: String, default: '48rpx' },
  cardCount:   { type: Number, default: 2 },
  cardHeight:  { type: String, default: '180rpx' },
  spoonHeight: { type: String, default: '160rpx' },
  spoonWidth:  { type: String, default: '240rpx' },
})
</script>

<style scoped>
.ls-wrap { width: 100%; }
.ls-wrap.full {
  min-height: 60vh;
  display: flex; align-items: center; justify-content: center;
}

.ls-inline {
  display: flex; align-items: center; gap: 2%;
  padding: 2% 4%;
  font-size: var(--t-sm); font-weight: 300;
  color: var(--c-text-2);
}
.ls-dot {
  width: 12rpx; height: 12rpx;
  background: var(--c-primary);
  border-radius: 50%;
  animation: ls-bounce 1s ease-in-out infinite;
}
@keyframes ls-bounce {
  0%, 100% { transform: scale(0.6); opacity: .55; }
  50%      { transform: scale(1.2); opacity: 1; }
}
.ls-text { font-weight: 300; color: var(--c-text-2); }

.ls-bars, .ls-cards {
  display: flex; flex-direction: column; gap: 3%;
  padding: 4%;
}
.ls-spoon {
  display: flex; flex-direction: column; align-items: center; gap: 3%;
  padding: 12% 0;
}

.ls-empty { text-align: center; padding: 17% 6%; }
.empty-icon { display: block; margin: 0 auto 4%; }
.empty-title { display: block; font-size: 93.75%; font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.empty-hint  { display: block; font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); margin-top: 2%; }
</style>