<template>
  <view class="page page-bg-ethereal" v-if="family">
    <CustomNav title="家庭详情" tone="glass" />

    <view class="header-block">
      <view class="title-row">
        <view class="title-icon-wrap">
          <Icon
            :name="family.role === 'CHEF' ? 'chef-hat' : family.role === 'OWNER' ? 'crown' : 'home'"
            size="44rpx"
            tone="white"
          />
        </view>
        <text class="title">{{ family.name }}</text>
      </view>
      <text class="subtitle">{{ roleText(family.role) }} · 码 {{ family.code }}</text>
    </view>

    <view class="info-card glass-card-2">
      <view class="info-row">
        <text class="info-label">加入码</text>
        <text class="info-code">{{ family.code }}</text>
        <view class="copy-btn pressable" @tap="copyCode">
          <Icon name="copy" size="22rpx" tone="white" />
          <text>复制</text>
        </view>
      </view>
      <view class="info-row" v-if="family.role === 'OWNER'">
        <text class="info-label">分享链接</text>
        <text class="info-link" @tap="copyLink">复制链接</text>
      </view>
    </view>

    <!-- 主厨状态 -->
    <view class="chef-card glass-card-2" v-if="chefInfo">
      <view class="chef-title-row">
        <Icon name="chef-hat" size="32rpx" tone="chef" />
        <text class="chef-title">主厨状态</text>
      </view>
      <view class="chef-status">
        <text class="chef-name" v-if="chefInfo.hasChef">{{ chefInfo.chefNickname }}</text>
        <text class="chef-none" v-else>还没人认领主厨</text>
      </view>

      <TapBurst :icons="['chef-hat','heart']" :count="8" v-if="!isChef && !chefInfo?.hasChef">
        <button class="btn-chef claim-btn pressable" @tap="doClaimChef">
          认领主厨
        </button>
      </TapBurst>
      <TapBurst :icons="['sparkle','heart']" :count="6" v-else-if="isChef">
        <button class="btn-ghost resign-btn pressable" @tap="openConfirm('resign')">退出主厨</button>
      </TapBurst>
    </view>

    <!-- 成员列表 -->
    <view class="members-card glass-card-2">
      <view class="members-title-row">
        <Icon name="family" size="32rpx" tone="primary" />
        <text class="members-title">家庭成员 ({{ members.length }})</text>
      </view>
      <view
        v-for="m in members"
        :key="m.userId"
        class="member-row"
      >
        <Icon
          :name="m.role === 'CHEF' ? 'chef-hat' : m.role === 'OWNER' ? 'crown' : 'user'"
          size="40rpx"
          tone="primary"
        />
        <text class="m-name">{{ m.nickname || '匿名' }}</text>
        <text class="m-role tag-gray">{{ roleText(m.role) }}</text>
        <view
          v-if="family.role === 'OWNER' && m.userId !== meUserId"
          class="m-kick pressable"
          @tap="askKick(m)"
        >踢人</view>
      </view>
    </view>

    <!-- 退出按钮（非OWNER） -->
    <view class="bottom" v-if="family.role !== 'OWNER'">
      <button class="btn-danger leave-btn pressable" @tap="openConfirm('leave')">退出此家庭</button>
    </view>

    <!-- 通用 ConfirmDialog -->
    <ConfirmDialog
      :visible="dialog.visible"
      :title="dialog.title"
      :content="dialog.content"
      :tone="dialog.tone"
      :icon="dialog.icon"
      :confirm-text="dialog.confirmText"
      :cancel-text="dialog.cancelText"
      @confirm="onDialogConfirm"
      @cancel="dialog.visible = false"
    />
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  getMyFamilies, getFamilyMembers, claimChef, resignChef, kickMember,
  leaveFamily, getFamilyChefStatus, getUserInfo
} from '../../utils/request.js'
import { ensureSubscribeBulk, warmPushConfig, CHEF_QUOTA_ON_CLAIM } from '../../utils/subscribe.js'
import Icon from '../../components/Icon.vue'
import TapBurst from '../../components/TapBurst.vue'
import CustomNav from '../../components/CustomNav.vue'
import ConfirmDialog from '../../components/ConfirmDialog.vue'

const familyId = ref(null)
const family = ref(null)
const members = ref([])
const chefInfo = ref(null)
const meUserId = ref(null)
const isChef = ref(false)

const dialog = reactive({ visible: false, title: '', content: '', tone: 'primary', icon: '', confirmText: '确定', action: null, payload: null })

const roleText = (r) => ({ OWNER: '创建者', CHEF: '主厨', MEMBER: '成员' })[r] || r

onLoad((options) => {
  let fid = parseInt(options.id)
  // 兜底：没传 id 或 id 非法时，回退到当前用户的 active family（避免任何路径走到这页
  // 都显示「家庭不存在」然后被 navigateBack 弹回去）
  if (isNaN(fid) || fid <= 0) {
    const info = getUserInfo() || {}
    const fallback = (info.families || []).find(f => f.active) || info.families?.[0]
    fid = fallback?.familyId
  }
  familyId.value = fid
  meUserId.value = getUserInfo()?.userId
  // 预热模板配置：ensureSubscribeBulk 要在点击回调里同步读到模板 ID，不能等它现拉
  warmPushConfig('CHEF')
  loadAll()
})

const loadAll = async () => {
  try {
    const list = await getMyFamilies() || []
    const f = list.find(x => x.familyId === familyId.value)
    if (!f) {
      uni.showToast({ title: '家庭不存在', icon: 'none' })
      setTimeout(() => uni.navigateBack(), 1000)
      return
    }
    family.value = { ...f }
    isChef.value = f.role === 'CHEF'
    await Promise.all([loadMembers(), loadChefStatus()])
  } catch (e) {}
}

const loadMembers = async () => {
  try {
    members.value = await getFamilyMembers(familyId.value) || []
  } catch (e) {}
}

const loadChefStatus = async () => {
  try {
    chefInfo.value = await getFamilyChefStatus(familyId.value)
  } catch (e) {}
}

const copyCode = () => {
  uni.setClipboardData({ data: family.value.code, success: () => uni.showToast({ title: '加入码已复制', icon: 'success' }) })
}

const copyLink = () => {
  const link = `http://192.168.x.x:8006/join/${family.value.code}`
  uni.setClipboardData({ data: link, success: () => uni.showToast({ title: '链接已复制', icon: 'success' }) })
}

const doClaimChef = async () => {
  // ⚠️ 顺序很关键：必须在 await claimChef 之前**同步**拉起订阅面板。
  //    requestSubscribeMessage 要求调用处在用户点击的手势链里，先 await 一个网络请求再拉面板，
  //    手势就丢了，微信会静默失败（不报错、也不弹窗）。
  const granted = ensureSubscribeBulk('CHEF', CHEF_QUOTA_ON_CLAIM)
  try {
    await claimChef(familyId.value)
    try { uni.vibrateShort({ type: 'medium' }) } catch (e) {}
    // 面板在点击瞬间就弹过了，这里几乎立即返回，不会拖慢反馈。
    // 注意：这里只说「授权了几次」，不说「能收几条」——服务端额度以微信实际下发结果为准
    // （真发失败会回 43101 并清空本地额度），别给用户许下我们保证不了的承诺。
    const n = await granted
    if (n > 1) {
      uni.showToast({ title: `主厨已就位，提醒已授权 ${n} 次`, icon: 'none' })
    } else {
      uni.showToast({ title: '已成为主厨！', icon: 'success' })
    }
    loadAll()
    uni.$emit('family:changed')
  } catch (e) {
    const msg = (e && (e.message || e.errMsg)) || '认领失败，请稍后再试'
    if (typeof msg === 'string' && !/网络/.test(msg)) {
      uni.showToast({ title: msg, icon: 'none' })
    }
  }
}

const openConfirm = (kind) => {
  if (kind === 'resign') {
    dialog.title = '退出主厨？'
    dialog.content = '退出后其他家庭成员可认领主厨位'
    dialog.tone = 'chef'
    dialog.icon = 'chef-hat'
    dialog.confirmText = '退出主厨'
    dialog.action = 'resign'
  } else if (kind === 'leave') {
    dialog.title = '退出家庭？'
    dialog.content = '退出后将无法查看该家庭的菜单'
    dialog.tone = 'danger'
    dialog.icon = 'logout'
    dialog.confirmText = '退出家庭'
    dialog.action = 'leave'
  }
  dialog.visible = true
}

const askKick = (m) => {
  dialog.title = '踢出成员？'
  dialog.content = `将「${m.nickname || '匿名'}」移出家庭`
  dialog.tone = 'danger'
  dialog.icon = 'close'
  dialog.confirmText = '踢出'
  dialog.action = 'kick'
  dialog.payload = m
  dialog.visible = true
}

const onDialogConfirm = async () => {
  dialog.visible = false
  try {
    if (dialog.action === 'resign') {
      await resignChef(familyId.value)
      uni.showToast({ title: '已退出主厨', icon: 'success' })
      loadAll()
      uni.$emit('family:changed')
    } else if (dialog.action === 'leave') {
      await leaveFamily(familyId.value)
      uni.showToast({ title: '已退出', icon: 'success' })
      setTimeout(() => uni.navigateBack(), 800)
    } else if (dialog.action === 'kick') {
      const m = dialog.payload
      if (!m) return
      await kickMember(familyId.value, m.userId)
      uni.showToast({ title: '已踢出', icon: 'success' })
      loadAll()
    }
  } catch (e) {
    const msg = (e && (e.message || e.errMsg)) || '操作失败，请稍后再试'
    if (typeof msg === 'string' && !/网络/.test(msg)) {
      uni.showToast({ title: msg, icon: 'none' })
    }
  }
}
</script>

<style scoped>
/* ============= 甜美少女粉·家庭详情 ============= */
.page { padding-bottom: 10%; }

/* 头部：浮在渐变上的纯文字 + 毛玻璃图标圆 */
.header-block {
  padding: 4% 5% 6%;
  color: var(--c-text);
}
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
.title {
  font-size: var(--t-xxl); font-weight: 300; color: var(--c-text);
  letter-spacing: 2rpx;
  text-shadow: 0 4rpx 16rpx rgba(255,123,148,.22);
}
.subtitle {
  display: block; font-size: var(--t-sm);
  font-weight: 300;
  color: var(--c-text-2);
  margin-top: 1.5%;
  letter-spacing: 1rpx;
}

/* 三张卡片（info / chef / members）用毛玻璃 */
.info-card, .chef-card, .members-card {
  margin: 4%;
  padding: 5% 5% 6%;
  border-radius: 32rpx;
}

.info-row {
  display: flex;
  align-items: center;
  padding: 2% 0;
  font-size: var(--t-sm);
  font-weight: 400;
}
.info-label { color: var(--c-deep-rose); width: 25%; font-weight: 400; letter-spacing: 1rpx; }
.info-code {
  flex: 1;
  font-size: var(--t-xl);
  font-weight: 600;
  letter-spacing: 6rpx;
  color: var(--c-deep-rose);
}
/* 复制按钮：珊瑚粉实色（点缀突出重点操作） */
.copy-btn {
  background: var(--g-primary);
  color: white;
  padding: 4rpx 18rpx;
  border-radius: 999rpx;
  font-size: var(--t-xs);
  font-weight: 400;
  display: flex; align-items: center; gap: 1%;
  box-shadow: var(--glow-soft);
}
.info-link { flex: 1; color: var(--c-primary); font-weight: 400; }

.chef-title-row { display: flex; align-items: center; gap: 2%; margin-bottom: 2%; }
.chef-title { font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.chef-status { margin-bottom: 3%; }
.chef-name { font-size: var(--t-lg); font-weight: 500; color: var(--c-chef); display: block; }
.chef-none { font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); display: block; }

.claim-btn {
  margin-top: 1%;
  font-size: var(--t-md); font-weight: 400 !important;
  letter-spacing: 4rpx !important;
  padding: 3% 0; width: 100%;
}
.resign-btn {
  margin-top: 1%;
  font-size: var(--t-md); font-weight: 400 !important;
  letter-spacing: 4rpx !important;
  padding: 3% 0; width: 100%;
}

.members-title-row { display: flex; align-items: center; gap: 2%; margin-bottom: 3%; }
.members-title { font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.member-row {
  display: flex;
  align-items: center;
  gap: 2%;
  padding: 3% 0;
  border-bottom: 1rpx solid rgba(122,74,90,.15);  /* 半透粉线 */
}
.member-row:last-child { border-bottom: none; }
.m-name { flex: 1; font-size: var(--t-md); font-weight: 400; color: var(--c-deep-rose); }
.m-role { font-size: 20rpx; padding: 2rpx 10rpx; border-radius: 6rpx; white-space: nowrap; font-weight: 400; }
.m-kick { color: var(--c-danger); font-size: var(--t-sm); padding: 1% 3%; font-weight: 400; }

.bottom { padding: 6%; }
.leave-btn {
  width: 100%; padding: 4%; font-size: var(--t-md);
  font-weight: 400 !important;
  letter-spacing: 4rpx !important;
}
</style>