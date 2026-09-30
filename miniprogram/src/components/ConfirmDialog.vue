<template>
  <view v-if="visible" class="cd-mask" @touchmove.stop.prevent="noop" @tap="onMask">
    <view class="cd-card anim-bounce-in" @tap.stop @touchmove.stop>
      <view class="cd-icon-wrap" :class="`tone-${tone}`">
        <Icon :name="iconName" size="64rpx" :tone="iconTone" />
      </view>
      <text class="cd-title" v-if="title">{{ title }}</text>
      <text class="cd-content" v-if="content">{{ content }}</text>
      <slot></slot>
      <view class="cd-actions" :class="{ single: !cancelText || singleButton }">
        <button v-if="cancelText && !singleButton" class="cd-btn cancel" @tap="onCancel">{{ cancelText }}</button>
        <button class="cd-btn ok" :class="`is-${tone}`" :loading="loading" @tap="onConfirm">{{ confirmText }}</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import Icon from './Icon.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '' },
  content: { type: String, default: '' },
  confirmText: { type: String, default: '确定' },
  cancelText: { type: String, default: '取消' },
  tone: { type: String, default: 'primary' }, // primary | chef | danger | warn
  icon: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  maskClosable: { type: Boolean, default: true },
  /** 只显示确认按钮（用作信息/选择器弹窗） */
  singleButton: { type: Boolean, default: false },
})

const emit = defineEmits(['confirm', 'cancel', 'update:visible'])

const iconName = computed(() => {
  if (props.icon) return props.icon
  if (props.tone === 'danger') return 'close'
  if (props.tone === 'warn')   return 'sparkle'
  if (props.tone === 'chef')   return 'chef-hat'
  return 'heart'
})

const iconTone = computed(() => {
  if (props.tone === 'chef') return 'chef'
  return 'primary'
})

const noop = () => {}
const onMask = () => {
  if (props.maskClosable) onCancel()
}
const onConfirm = () => emit('confirm')
const onCancel  = () => emit('cancel')
</script>

<style scoped>
.cd-mask {
  position: fixed; inset: 0; z-index: 9999;
  background: rgba(122,74,90,0.45);
  display: flex; align-items: center; justify-content: center;
  padding: 0 4%;
}
.cd-card {
  width: 80%;
  max-height: 70vh;
  overflow-y: auto;
  background: rgba(255,255,255,0.92);
  backdrop-filter: blur(40rpx);
  -webkit-backdrop-filter: blur(40rpx);
  border: 1rpx solid rgba(255,255,255,0.7);
  border-radius: 40rpx;
  padding: 8% 6% 5%;
  display: flex; flex-direction: column; align-items: center;
  box-shadow: var(--glow-deep);
}
.cd-icon-wrap {
  width: 112rpx; height: 112rpx;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  margin-bottom: 4%;
}
.cd-icon-wrap.tone-primary { background: rgba(255,123,148,0.12); }
.cd-icon-wrap.tone-chef    { background: rgba(255,255,255,0.7); }
.cd-icon-wrap.tone-danger  { background: var(--c-danger-bg); }
.cd-icon-wrap.tone-warn    { background: var(--c-pending-bg); }

.cd-title {
  font-size: var(--t-xl); font-weight: 500;
  color: var(--c-deep-rose);
  letter-spacing: 1rpx;
  margin-bottom: 2%;
  text-align: center;
}
.cd-content {
  font-size: var(--t-md); font-weight: 300;
  color: var(--c-text-2);
  line-height: 1.55;
  text-align: center;
  margin-bottom: 5%;
  white-space: pre-line; /* 支持 content 多行 */
}
.cd-actions {
  display: flex;
  gap: 3%;
  width: 100%;
  margin-top: 2%;
}
.cd-actions.single { justify-content: center; }
.cd-btn {
  flex: 1;
  border-radius: 999rpx;
  font-size: var(--t-md);
  font-weight: 400;
  letter-spacing: 2rpx;
  padding: 3% 0;
  border: none;
  line-height: 1.5;
}
.cd-btn.cancel {
  background: rgba(255,255,255,0.75);
  color: var(--c-text-2);
}
.cd-btn.ok {
  background: var(--g-primary);
  color: white;
  box-shadow: var(--glow-pink);
}
/* tone 变体：danger/chef/warn 切换确认按钮颜色 */
.cd-btn.ok.is-danger {
  background: var(--c-danger) !important;
  color: white !important;
}
.cd-btn.ok.is-chef {
  background: var(--g-chef) !important;
  color: white !important;
}
.cd-btn.ok.is-warn {
  background: var(--c-pending) !important;
  color: white !important;
}

/* 自包含：组件样式隔离下全局 .anim-bounce-in 失效 */
.anim-bounce-in { animation: sk-bounce-in .55s cubic-bezier(.34,1.56,.64,1) both; }
@keyframes sk-bounce-in {
  0%   { transform: scale(.6); opacity: 0; }
  60%  { transform: scale(1.12); opacity: 1; }
  100% { transform: scale(1); }
}
</style>
