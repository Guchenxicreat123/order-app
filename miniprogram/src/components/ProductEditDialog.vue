<template>
  <view class="edit-dialog" v-if="visible">
    <view class="dialog-mask" @tap="$emit('update:visible', false)"></view>
    <view class="dialog-content">
      <view class="dialog-header">
        <text class="dialog-title">编辑菜品</text>
        <view class="dialog-close pressable" @tap="$emit('update:visible', false)">
          <Icon name="close" size="28rpx" tone="primary" />
        </view>
      </view>
      <scroll-view scroll-y class="dialog-body">
        <view class="form-item">
          <text class="form-label">菜名</text>
          <input class="form-input" v-model="form.name" placeholder="菜名" maxlength="64" />
        </view>
        <view class="form-item">
          <text class="form-label">图标</text>
          <view class="emoji-picker-row">
            <view
              v-for="opt in emojiOptions"
              :key="opt"
              class="emoji-pick pressable"
              :class="{ active: form.imageEmoji === opt }"
              @tap="form.imageEmoji = opt"
            >
              <text class="emoji-pick-text">{{ opt }}</text>
            </view>
          </view>
          <text class="form-hint">选一个图标代表这道菜</text>
        </view>
        <view class="form-item">
          <text class="form-label">分类</text>
          <picker :value="categoryIndex" :range="categoryOptions" range-key="name" @change="onCategoryChange">
            <view class="form-picker">{{ form.categoryName || '请选择' }} ▼</view>
          </picker>
        </view>
        <view class="form-item">
          <text class="form-label">辣度</text>
          <view class="radio-group">
            <view class="radio-item" :class="{ active: form.spiceLevel === 0 }" @tap="form.spiceLevel = 0">不辣</view>
            <view class="radio-item" :class="{ active: form.spiceLevel === 1 }" @tap="form.spiceLevel = 1">微辣</view>
            <view class="radio-item" :class="{ active: form.spiceLevel === 2 }" @tap="form.spiceLevel = 2">重辣</view>
          </view>
        </view>
        <view class="form-item">
          <text class="form-label">描述</text>
          <textarea class="form-textarea" v-model="form.description" placeholder="简短描述" maxlength="255" />
        </view>
      </scroll-view>
      <view class="dialog-footer">
        <button class="btn-ghost dialog-cancel" @tap="$emit('update:visible', false)">取消</button>
        <button class="btn-primary dialog-confirm" :loading="saving" @tap="$emit('save')">保存</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import Icon from './Icon.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  form: { type: Object, required: true },
  categories: { type: Array, default: () => [] },
  saving: { type: Boolean, default: false }
})
const emit = defineEmits(['update:visible', 'save', 'category-change'])

const emojiOptions = ['🍳','🥘','🍲','🍜','🍝','🍛','🥗','🍅','🥔','🥦','🥬','🥕','🧅','🧄','🌽','🍆','🥚','🥩','🍗','🍖','🐟','🦐','🥑','🍋','🌶️','🧂','🍯','🧀','🥛','🍰','🍚','🍤','🥟','🍡','🥨']

const categoryOptions = computed(() =>
  props.categories.map(c => ({ id: c.id, name: c.name }))
)
const categoryIndex = computed(() =>
  props.categories.findIndex(c => c.id === props.form.categoryId)
)

const onCategoryChange = (e) => {
  const idx = e.detail.value
  emit('category-change', props.categories[idx])
}
</script>

<style scoped>
.edit-dialog { position: fixed; top: 0; left: 0; right: 0; bottom: 0; z-index: 200; }
.dialog-mask { position: absolute; top: 0; left: 0; right: 0; bottom: 0; background: rgba(122,74,90,0.45); }
.dialog-content {
  position: absolute; bottom: 0; left: 0; right: 0;
  background: rgba(255,255,255,0.94);
  backdrop-filter: blur(40rpx);
  -webkit-backdrop-filter: blur(40rpx);
  border-radius: 40rpx 40rpx 0 0;
  max-height: 80vh; display: flex; flex-direction: column;
}
.dialog-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 5% 5% 4%; border-bottom: 1rpx solid rgba(122,74,90,.18);
}
.dialog-title { font-size: var(--t-xl); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.dialog-close { padding: 1%; }
.dialog-body { padding: 4% 5%; flex: 1; }
.dialog-footer {
  display: flex; gap: 4%;
  padding: 4% 5%;
  padding-bottom: calc(4% + constant(safe-area-inset-bottom));
  padding-bottom: calc(4% + env(safe-area-inset-bottom));
  border-top: 1rpx solid rgba(122,74,90,.18);
}
.dialog-cancel {
  flex: 1; height: 88rpx;
  font-size: var(--t-md); font-weight: 400 !important;
  letter-spacing: 2rpx !important;
  background: rgba(255,255,255,0.7) !important;
  color: var(--c-text-2) !important;
  border: 1rpx solid rgba(122,74,90,.18) !important;
  border-radius: 999rpx !important;
  line-height: 1.5;
}
.dialog-confirm {
  flex: 1; height: 88rpx;
  font-size: var(--t-md); font-weight: 400 !important;
  letter-spacing: 2rpx !important;
  background: var(--g-primary) !important;
  color: white !important;
  border: none !important;
  border-radius: 999rpx !important;
  box-shadow: var(--glow-pink) !important;
  line-height: 1.5;
}
.dialog-confirm[disabled] {
  background: var(--c-text-3) !important;
  opacity: .55;
}

/* 自包含：组件样式隔离下 .pressable 失效，此处补一份等价 */
.pressable { transition: transform .18s ease, opacity .18s ease; }
.pressable:active { transform: scale(.96); opacity: .85; }

.form-item { margin-bottom: 5%; }
.form-label { font-size: var(--t-sm); font-weight: 400; color: var(--c-text-2); margin-bottom: 2%; display: block; }
.form-hint { display: block; font-size: var(--t-xs); color: var(--c-text-3); margin-top: 1%; }
.form-input {
  width: 100%; height: 80rpx;
  background: rgba(255,255,255,0.7); border: 1rpx solid rgba(122,74,90,.2); border-radius: 16rpx;
  padding: 0 4%; font-size: var(--t-md); font-weight: 400; color: var(--c-deep-rose); box-sizing: border-box;
}
.form-picker {
  height: 80rpx; background: rgba(255,255,255,0.7); border: 1rpx solid rgba(122,74,90,.2); border-radius: 16rpx;
  padding: 0 4%; font-size: var(--t-md); font-weight: 400; color: var(--c-deep-rose);
  display: flex; align-items: center;
}
.form-textarea {
  width: 100%; background: rgba(255,255,255,0.7); border: 1rpx solid rgba(122,74,90,.2); border-radius: 16rpx;
  padding: 3% 4%; font-size: var(--t-md); font-weight: 400; color: var(--c-deep-rose);
  box-sizing: border-box; min-height: 120rpx;
}
.emoji-picker-row { display: flex; gap: 2%; flex-wrap: wrap; }
.emoji-pick {
  width: 80rpx; height: 80rpx;
  background: rgba(255,255,255,0.7);
  border: 2rpx solid rgba(122,74,90,.2);
  border-radius: 16rpx;
  display: flex; align-items: center; justify-content: center;
}
.emoji-pick.active {
  background: rgba(255,123,148,0.15);
  border-color: var(--c-primary);
}
.emoji-pick-text { font-size: var(--t-xxl); line-height: 1; }
.radio-group { display: flex; gap: 3%; }
.radio-item {
  flex: 1; height: 72rpx;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255,255,255,0.7); border: 2rpx solid rgba(122,74,90,.2); border-radius: 999rpx;
  font-size: var(--t-sm); font-weight: 400; color: var(--c-text-2);
}
.radio-item.active {
  background: rgba(255,123,148,0.15); border-color: var(--c-primary); color: var(--c-primary); font-weight: 400;
}
</style>