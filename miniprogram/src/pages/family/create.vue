<template>
  <view class="page page-bg-ethereal">
    <CustomNav title="创建家庭" tone="glass" />

    <!-- 头部装饰：标题 + 副标题浮在渐变上 -->
    <view class="header">
      <view class="title-row">
        <view class="title-icon-wrap">
          <Icon name="plus" size="44rpx" tone="white" />
        </view>
        <text class="header-title">给你的家起个名字</text>
      </view>
      <text class="header-subtitle">分享 6 位加入码给家人，他们就能一起用</text>
    </view>

    <!-- 毛玻璃表单卡 -->
    <view class="form-card glass-card-2">
      <view class="form-label">
        <Icon name="home" size="28rpx" tone="primary" />
        <text>家庭名称</text>
      </view>
      <input
        class="form-input"
        v-model="name"
        placeholder="如：温馨小家 / 我家"
        maxlength="20"
        :focus="true"
        confirm-type="done"
        @confirm="submit"
        placeholder-style="color:rgba(122,74,90,.60);text-align:center;"
      />

      <TapBurst :icons="['heart','sparkle','home']" :count="8">
        <button
          class="btn-primary submit-btn pressable"
          @tap="submit"
          :loading="submitting"
          :disabled="!name.trim()"
        >创建家庭</button>
      </TapBurst>

      <view class="tips">
        <text>创建后自动获得 6 位加入码，</text>
        <text>把码发给家人，他们扫码就能加入。</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import Icon from '@/components/Icon.vue'
import TapBurst from '@/components/TapBurst.vue'
import CustomNav from '@/components/CustomNav.vue'
import { ref } from 'vue'
import { createFamily, getMyFamilies, wxLogin } from '../../utils/request.js'

const name = ref('')
const submitting = ref(false)

// 确保带有效 token：先试自动登录（若尚未登录）
const ensureLogin = async () => {
  try {
    // request.js 在 401 时会自动调 wxLogin，但这里我们主动预热，避免先撞 401 再去登录
    const info = uni.getStorageSync('userInfo')
    if (!info || !info.token) {
      let code = uni.getStorageSync('deviceCode')
      if (!code) {
        code = 'dev_' + Date.now() + '_' + Math.floor(Math.random() * 10000)
        uni.setStorageSync('deviceCode', code)
      }
      // #ifdef MP-WEIXIN
      try {
        const r = await new Promise((resolve, reject) => {
          uni.login({ provider: 'weixin', success: resolve, fail: reject })
        })
        if (r && r.code) code = r.code
      } catch (e) {}
      // #endif
      await wxLogin(code)
    }
  } catch (e) {
    // 预热失败不影响后续提交，让 request.js 的 401 处理再兜一次
  }
}

const submit = async () => {
  if (!name.value.trim()) {
    uni.showToast({ title: '请输入家庭名称', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await ensureLogin()
    await createFamily(name.value.trim())
    try {
      const list = await getMyFamilies() || []
      const userInfo = uni.getStorageSync('userInfo') || {}
      const active = list.find(f => f.active) || list[0] || null
      userInfo.families = list
      userInfo.activeFamilyId = active ? active.familyId : null
      uni.setStorageSync('userInfo', userInfo)
    } catch (e) {}
    try { uni.vibrateShort({ type: 'light' }) } catch (e) {}
    uni.showToast({ title: '家建好了', icon: 'success' })
    setTimeout(() => {
      uni.switchTab({ url: '/pages/index/index' })
    }, 600)
  } catch (e) {
    // 把后端或网络错误展示给用户（之前是静默吞掉，看不到原因）
    const msg = (e && (e.message || e.errMsg)) || '创建失败，请稍后再试'
    if (typeof msg === 'string' && !/网络/.test(msg)) {
      uni.showToast({ title: msg, icon: 'none' })
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
/* ============= 甜美少女粉·创建家庭 ============= */
.page { padding-bottom: 14%; }

/* 头部：浮在渐变上，纯文字，无卡片 */
.header {
  padding: 2% 5% 6%;
  color: var(--c-text);
}
.title-row {
  display: flex; align-items: center; gap: 3%;
}
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
  font-size: var(--t-xxl); font-weight: 300;
  color: var(--c-text);
  letter-spacing: 2rpx;
  text-shadow: 0 4rpx 16rpx rgba(255,123,148,.22);
}
.header-subtitle {
  display: block; font-size: var(--t-sm); font-weight: 300;
  color: var(--c-text-2);
  margin-top: 1.5%; letter-spacing: 1rpx;
}

/* 毛玻璃表单卡 */
.form-card {
  margin: 6% 4% 0;
  padding: 6% 5% 7%;
}
.form-label {
  display: flex; align-items: center; gap: 1.5%;
  font-size: var(--t-md); font-weight: 400; color: var(--c-deep-rose);
  margin-bottom: 3%;
  letter-spacing: 1rpx;
}

/* 输入框：毛玻璃 + 纤细字号 */
.form-input {
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(16rpx);
  -webkit-backdrop-filter: blur(16rpx);
  border: 2rpx solid rgba(255,255,255,0.6);
  border-radius: 32rpx;
  height: 160rpx;
  line-height: 156rpx;
  padding: 0 5%;
  font-size: 44rpx;
  font-weight: 400;
  color: var(--c-deep-rose);
  width: 100%;
  box-sizing: border-box;
  text-align: center;
  letter-spacing: 2rpx;
  text-indent: 2rpx;
}
.form-input:focus {
  border-color: rgba(255,123,148,0.6);
  box-shadow: var(--glow-pink);
}

/* 提交按钮：珊瑚粉主渐变，字重纤细 */
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
  letter-spacing: 1rpx;
}
</style>