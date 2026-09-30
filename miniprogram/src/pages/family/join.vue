<template>
  <view class="page page-bg-ethereal">
    <CustomNav title="加入家庭" tone="glass" />

    <view class="header">
      <view class="title-row">
        <view class="title-icon-wrap">
          <Icon name="family" size="44rpx" tone="white" />
        </view>
        <text class="header-title">输入家人给你的码</text>
      </view>
      <text class="header-subtitle">把家人们汇到一起点菜</text>
    </view>

    <view class="form-card glass-card-2">
      <view class="form-label">
        <Icon name="copy" size="28rpx" tone="primary" />
        <text>6 位加入码</text>
      </view>
      <input
        class="form-input"
        v-model="code"
        type="text"
        placeholder="ABC123"
        maxlength="6"
        :adjust-position="false"
        placeholder-style="color:rgba(122,74,90,.60);font-weight:600;text-align:center;"
      />

      <TapBurst :icons="['heart','family','sparkle']" :count="8">
        <button
          class="btn-primary submit-btn pressable"
          @tap="submit"
          :loading="submitting"
          :disabled="code.trim().length !== 6"
        >加入家庭</button>
      </TapBurst>

      <view class="tips">
        <text>加入码不区分大小写</text>
        <text>同一码只能加入一次</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import Icon from '@/components/Icon.vue'
import TapBurst from '@/components/TapBurst.vue'
import CustomNav from '@/components/CustomNav.vue'
import { ref } from 'vue'
import { joinFamily, getMyFamilies, wxLogin } from '../../utils/request.js'

const code = ref('')
const submitting = ref(false)

const ensureLogin = async () => {
  try {
    const info = uni.getStorageSync('userInfo')
    if (!info || !info.token) {
      let payload = uni.getStorageSync('deviceCode')
      if (!payload) {
        payload = 'dev_' + Date.now() + '_' + Math.floor(Math.random() * 10000)
        uni.setStorageSync('deviceCode', payload)
      }
      // #ifdef MP-WEIXIN
      try {
        const r = await new Promise((resolve, reject) => {
          uni.login({ provider: 'weixin', success: resolve, fail: reject })
        })
        if (r && r.code) payload = r.code
      } catch (e) {}
      // #endif
      await wxLogin(payload)
    }
  } catch (e) {}
}

const submit = async () => {
  const c = code.value.trim().toUpperCase()
  if (c.length !== 6) {
    uni.showToast({ title: '请输入 6 位加入码', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await ensureLogin()
    await joinFamily(c)
    try {
      const list = await getMyFamilies() || []
      const userInfo = uni.getStorageSync('userInfo') || {}
      const active = list.find(f => f.active) || list[0] || null
      userInfo.families = list
      userInfo.activeFamilyId = active ? active.familyId : null
      uni.setStorageSync('userInfo', userInfo)
    } catch (e) {}
    try { uni.vibrateShort({ type: 'light' }) } catch (e) {}
    uni.showToast({ title: '进家了', icon: 'success' })
    setTimeout(() => {
      uni.switchTab({ url: '/pages/index/index' })
    }, 600)
  } catch (e) {
    const msg = (e && (e.message || e.errMsg)) || '加入失败，请稍后再试'
    if (typeof msg === 'string' && !/网络/.test(msg)) {
      uni.showToast({ title: msg, icon: 'none' })
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
/* ============= 甜美少女粉·加入家庭 ============= */
.page { padding-bottom: 14%; }

.header { padding: 2% 5% 6%; color: var(--c-text); }
.title-row { display: flex; align-items: center; gap: 3%; }
.title-icon-wrap {
  width: 80rpx; height: 80rpx;
  background: rgba(255,255,255,.35);
  border: 2rpx solid rgba(255,255,255,.55);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  box-shadow: var(--glow-pink);
}
.header-title {
  font-size: var(--t-xxl); font-weight: 300; color: var(--c-text);
  letter-spacing: 2rpx;
  text-shadow: 0 4rpx 16rpx rgba(255,123,148,.22);
}
.header-subtitle {
  display: block; font-size: var(--t-sm); font-weight: 300;
  color: var(--c-text-2);
  margin-top: 1.5%; letter-spacing: 1rpx;
}

/* 毛玻璃表单卡 */
.form-card { margin: 6% 4% 0; padding: 6% 5% 7%; }
.form-label {
  display: flex; align-items: center; gap: 1.5%;
  font-size: var(--t-md); font-weight: 400; color: var(--c-deep-rose);
  margin-bottom: 3%;
  letter-spacing: 1rpx;
}

/* 输入框：毛玻璃 + 大字号（输入码要醒目） */
.form-input {
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(16rpx);
  -webkit-backdrop-filter: blur(16rpx);
  border: 2rpx solid rgba(255,255,255,0.6);
  border-radius: 32rpx;
  height: 160rpx;
  line-height: 156rpx;
  padding: 0;
  font-size: 56rpx;
  font-weight: 600;
  color: var(--c-deep-rose);
  width: 100%;
  box-sizing: border-box;
  text-align: center;
  letter-spacing: 4rpx;
  text-indent: 4rpx;
}
.form-input:focus {
  border-color: rgba(255,123,148,0.6);
  box-shadow: var(--glow-pink);
}

.submit-btn {
  margin-top: 7%;
  width: 100%;
  padding: 4%;
  font-size: var(--t-md);
  font-weight: 400 !important;
  letter-spacing: 4rpx !important;
}
.tips {
  margin-top: 4%;
  font-size: var(--t-sm);
  font-weight: 300;
  color: var(--c-text-2);
  text-align: center;
  line-height: 1.7;
  display: flex; flex-direction: column; gap: 1%;
  letter-spacing: 1rpx;
}
</style>