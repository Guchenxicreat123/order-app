<template>
  <!-- v-if 双保险：调用方即使漏了外层 v-if，这里也不会把 input 放进渲染树。
       只要含 <input type="nickname"> 的节点存在，微信就可能带出昵称候选并聚焦。 -->
  <view v-if="visible" class="psc-root">
    <view class="psc-mask" :class="{ show: visible }" @tap="onMaskTap">
      <view class="psc-card anim-bounce-in" @tap.stop="noop">
        <view class="psc-header">
          <text class="psc-title">起个名字吧</text>
          <text class="psc-sub">让家人知道点菜的是谁</text>
        </view>

        <!-- 昵称：type="nickname" 会带出微信昵称候选，用户确认后自动填入。
             ⚠️ 不要加 focus —— 自动聚焦会在弹层刚出现时直接唤起输入法。 -->
        <view class="psc-field">
          <text class="psc-label">昵称</text>
          <input
            class="psc-input"
            type="nickname"
            :value="nickname"
            @input="onNicknameInput"
            placeholder="点击这里，选择微信昵称"
            placeholder-class="psc-placeholder"
            maxlength="20"
          />
        </view>

        <view class="psc-actions">
          <button class="psc-btn-ghost" @tap="onSkip">跳过</button>
          <button
            class="psc-btn-primary"
            :disabled="saving || !nickname.trim()"
            @tap="onSave"
          >{{ saving ? '保存中…' : '保存' }}</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, watch } from 'vue'
import { updateNickname, getUserInfo, setUserInfo } from '../utils/request.js'

const props = defineProps({
  visible: { type: Boolean, default: false }
})
const emit = defineEmits(['close', 'saved'])

const nickname = ref('')
const saving = ref(false)
// 防止「登录后刚弹出、手指还在屏幕上」被误触关闭
const armed = ref(false)
let armTimer = 0

// immediate: true —— 外层有 v-if，组件是在 visible 已为 true 时才挂载的，
// 不加 immediate 的话这个 watch 永远不会首次触发，armed 会一直是 false（遮罩点不动）。
watch(() => props.visible, (v) => {
  if (v) {
    const u = getUserInfo() || {}
    // 默认昵称「用户xxxxxx」不预填：否则用户以为已经起好名字了。
    // 留空才能露出 placeholder「点击这里，选择微信昵称」。
    nickname.value = isDefaultNickname(u.nickname) ? '' : (u.nickname || '')
    // 打开后 450ms 内忽略遮罩点击，避免误触
    armed.value = false
    try { clearTimeout(armTimer) } catch (e) {}
    armTimer = setTimeout(() => { armed.value = true }, 450)
  } else {
    armed.value = false
    try { clearTimeout(armTimer) } catch (e) {}
  }
}, { immediate: true })

/** 后端给的默认昵称（用户 + openId 后 6 位），以及旧版的「家人」 */
function isDefaultNickname(n) {
  const v = String(n || '').trim()
  if (!v) return true
  if (v === '家人') return true
  // 默认昵称 = 「用户」+ openId 后 6 位；真实 openid 含 - 和 _，所以字符类要放宽
  return /^用户[\w-]{1,12}$/.test(v)
}

function onNicknameInput(e) {
  nickname.value = e.detail.value || ''
}

function noop() {}

/** 点遮罩只关浮层。
 *  ⚠️ 这里也必须 markApplied()：否则用户点一次遮罩关掉后，每次回到首页 onShow
 *  都会重新判定「新用户 + 没起过名字」→ 弹层又冒出来（就是那个「关不掉的输入法」）。 */
function onMaskTap() {
  if (!armed.value) return
  markApplied()
  emit('close')
}

async function onSave() {
  const name = nickname.value.trim()
  if (!name) {
    uni.showToast({ title: '昵称不能为空', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await updateNickname(name)
    markApplied()
    uni.showToast({ title: '已保存', icon: 'success' })
    emit('saved', { nickname: name })
    emit('close')
  } catch (e) {
    const msg = (e && (e.message || e.msg)) || '昵称保存失败，请重试'
    uni.showToast({ title: String(msg).slice(0, 14), icon: 'none' })
  } finally {
    saving.value = false
  }
}

function onSkip() {
  // 「跳过」= 主动放弃本次设置，但也要打标记：
  // 否则用户每次回到首页 onShow 都会再弹一次，体验像「关不掉的弹窗」。
  markApplied()
  emit('close')
}

/**
 * 落地「本次引导已处理」标记。
 * ⚠️ 只标记 nicknameApplied，**不清 isNewUser**：
 *    isNewUser 由后端登录响应写入，前端改它没有意义（下次重登还会被写回 true）。
 *    判重真正的依据是 index.vue 里的 hasDefaultNickname()：昵称一旦不是
 *    「用户xxxxxx」默认值，就永久不再弹。
 */
function markApplied() {
  const info = getUserInfo() || {}
  if (!info.nicknameApplied) {
    info.nicknameApplied = true
    setUserInfo(info)
  }
  try { uni.$emit && uni.$emit('profile:nickname-saved') } catch (e) {}
}
</script>

<style scoped>
.psc-root { position: relative; z-index: 9999; }
/* 兜底隐藏：外层已有 v-if，这里再加一层 visibility 保险，
   即使 props 时序异常也绝不会出现「半可见的输入框」。 */
.psc-mask {
  position: fixed; inset: 0;
  background: rgba(122,74,90,0.45);
  display: flex; align-items: center; justify-content: center;
  padding: 0 8%;
  box-sizing: border-box;
  visibility: hidden;
  opacity: 0;
  transition: opacity .18s ease;
}
.psc-mask.show {
  visibility: visible;
  opacity: 1;
}
.psc-card {
  width: 100%; max-width: 640rpx;
  background: rgba(255,255,255,0.92);
  backdrop-filter: blur(40rpx);
  -webkit-backdrop-filter: blur(40rpx);
  border: 1rpx solid rgba(255,255,255,0.7);
  border-radius: 40rpx;
  padding: 7% 6% 5%;
  box-shadow: var(--glow-deep);
}
.psc-header {
  display: flex; flex-direction: column; align-items: center;
  margin-bottom: 6%;
}
.psc-title {
  font-size: var(--t-xl); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx;
}
.psc-sub {
  font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2);
  margin-top: 4rpx;
}
.psc-field {
  display: flex; flex-direction: column;
  margin-bottom: 6%;
}
.psc-label {
  font-size: var(--t-sm); font-weight: 400; color: var(--c-text-2);
  margin-bottom: 2%;
}
.psc-input {
  background: rgba(255,255,255,0.7); border: 1rpx solid rgba(122,74,90,.2);
  border-radius: 16rpx; padding: 5% 4%;
  font-size: var(--t-md); font-weight: 400; color: var(--c-deep-rose);
  box-sizing: border-box;
}
.psc-placeholder { color: var(--c-text-3); }
.psc-actions {
  display: flex; gap: 3%;
}
.psc-btn-ghost, .psc-btn-primary {
  flex: 1;
  border-radius: 999rpx !important;
  font-size: var(--t-md) !important;
  font-weight: 400 !important;
  letter-spacing: 2rpx !important;
  padding: 4% 0 !important;
  border: none !important;
  line-height: 1.4 !important;
}
.psc-btn-ghost {
  background: rgba(255,255,255,0.75) !important;
  color: var(--c-text-2) !important;
}
.psc-btn-primary {
  background: var(--g-primary) !important;
  color: white !important;
  box-shadow: var(--glow-pink) !important;
}
.psc-btn-primary[disabled] { opacity: .55; }
</style>
