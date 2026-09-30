<template>
  <view class="page page-bg-ethereal">
    <CustomNav title="我的家庭" tone="glass" />

    <!-- 创建/加入按钮：毛玻璃双列 -->
    <view class="actions">
      <view class="action-col">
        <TapBurst :icons="['plus','heart']" class="action-burst">
          <view class="action-btn glass-card pressable" @tap="goCreate">
            <view class="action-emoji-wrap">
              <Icon name="plus" size="64rpx" tone="primary" />
            </view>
            <text class="action-label">创建家庭</text>
          </view>
        </TapBurst>
      </view>
      <view class="action-col">
        <TapBurst :icons="['family','heart']" class="action-burst">
          <view class="action-btn glass-card pressable" @tap="goJoin">
            <view class="action-emoji-wrap">
              <Icon name="family" size="64rpx" tone="primary" />
            </view>
            <text class="action-label">加入家庭</text>
          </view>
        </TapBurst>
      </view>
    </view>

    <!-- 家庭列表 -->
    <view class="list" v-if="families.length > 0">
      <view
        v-for="f in families"
        :key="f.familyId"
        class="family-card glass-card pressable"
        :class="{ active: f.active }"
        @tap="selectFamily(f)"
      >
        <view class="fc-row1">
          <Icon :name="f.isOwner ? 'crown' : 'home'" size="40rpx" tone="primary" />
          <text class="fc-name">{{ f.name }}</text>
          <view class="fc-active" v-if="f.active">当前</view>
        </view>
        <view class="fc-row2">
          <text class="fc-role">{{ roleText(f.role) }}</text>
          <text class="fc-code">码：{{ f.code }}</text>
          <text class="fc-link" @tap.stop="goDetail(f)">详情 ›</text>
        </view>
      </view>
    </view>

    <view v-else class="empty anim-fade-up">

        <view class="empty-illus"><Icon name="family" size="120rpx" tone="primary" class="empty-icon" /></view>
      <text class="empty-title">还没有家庭</text>
      <text class="empty-hint">创建自己的家，或用 6 位加入码加入别人的家</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getMyFamilies, switchFamily } from '../../utils/request.js'
import Icon from '../../components/Icon.vue'
import TapBurst from '../../components/TapBurst.vue'
import CustomNav from '../../components/CustomNav.vue'


const families = ref([])
const loading = ref(false)

const loadData = async () => {
  if (loading.value) return
  loading.value = true
  try {
    const list = await getMyFamilies() || []
    families.value = list
    const userInfo = uni.getStorageSync('userInfo') || {}
    const active = list.find(f => f.active) || list[0] || null
    userInfo.families = list
    userInfo.activeFamilyId = active ? active.familyId : null
    uni.setStorageSync('userInfo', userInfo)
  } catch (e) {} finally {
    loading.value = false
  }
}

const roleText = (role) => {
  return { OWNER: '创建者', CHEF: '主厨', MEMBER: '成员' }[role] || role
}

const selectFamily = async (f) => {
  if (f.active) {
    uni.showToast({ title: '已是当前家庭', icon: 'none' })
    return
  }
  try {
    await switchFamily(f.familyId)
    uni.showToast({ title: `已切到「${f.name}」`, icon: 'success' })
    await loadData()
    uni.$emit('family:changed')
  } catch (e) {}
}

const goCreate = () => uni.navigateTo({ url: '/pages/family/create' })
const goJoin = () => uni.navigateTo({ url: '/pages/family/join' })
const goDetail = (f) => uni.navigateTo({ url: `/pages/family/detail?id=${f.familyId}` })

onShow(() => { loadData() })
</script>

<style scoped>
/* ============= 甜美少女粉·我的家庭 ============= */
.page { padding-bottom: 10%; }

.actions {
  display: flex; align-items: stretch; flex-wrap: nowrap;
  padding: 4% 4%;
}
.action-col {
  flex: 1 1 0; display: flex; min-width: 0;
}
.action-col + .action-col { margin-left: 24rpx; }
/* <TapBurst> 宿主撑满整列（之前修复 TapBurst 塌陷的根因代码要保留） */
.action-burst { flex: 1; display: flex; width: 100%; min-width: 0; }
.action-burst :deep(.tap-burst) { width: 100%; display: block; }

/* 毛玻璃动作按钮：白底 → 半透 + blur，圆角放大 */
.action-btn {
  flex: 1 1 0;
  border-radius: 32rpx;
  padding: 6% 4%;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 12rpx;
  width: 100%;
  min-height: 220rpx;
  box-sizing: border-box;
  box-shadow: var(--glow-soft);
}
/* 玻璃底色（叠在 .glass-card 基础上再叠加柔粉光） */
.action-btn.glass-card {
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
}

.action-emoji-wrap {
  width: 110rpx; height: 110rpx;
  background: rgba(255,255,255,0.7);
  border: 2rpx solid rgba(255,255,255,0.6);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  backdrop-filter: blur(16rpx);
  -webkit-backdrop-filter: blur(16rpx);
}
.action-label {
  font-size: var(--t-lg);
  font-weight: 400;
  color: var(--c-deep-rose);
  letter-spacing: 1rpx;
  max-width: 100%;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* 家庭列表卡 */
.list { padding: 0 4%; display: flex; flex-direction: column; gap: 24rpx; }
.family-card {
  padding: 4%;
  border-radius: 32rpx;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  box-shadow: var(--glow-soft);
}
/* 当前选中家庭：用珊瑚粉描边突出 */
.family-card.active {
  border: 2rpx solid rgba(255,123,148,0.6);
  box-shadow: var(--glow-pink);
}

.fc-row1 { display: flex; align-items: center; gap: 2%; }
.fc-name {
  flex: 1; font-size: var(--t-lg);
  font-weight: 500;
  color: var(--c-deep-rose);
  letter-spacing: 1rpx;
}
.fc-active {
  background: var(--g-primary);
  color: white;
  font-size: 20rpx;
  padding: 4rpx 14rpx;
  white-space: nowrap;
  border-radius: 999rpx;
  font-weight: 400;
}
.fc-row2 {
  display: flex; gap: 3%; margin-top: 2%;
  font-size: var(--t-xs); font-weight: 300;
  color: var(--c-text-2);
  align-items: center;
}
.fc-code { font-family: monospace; letter-spacing: 2rpx; }
.fc-link { color: var(--c-primary); margin-left: auto; }

.empty { text-align: center; padding: 15% 6%; color: var(--c-text-3); }
.empty-icon { display: block; margin: 0 auto 4%; }
.empty-title {
  display: block; font-size: var(--t-lg);
  font-weight: 400; color: var(--c-deep-rose);
  letter-spacing: 1rpx;
}
.empty-hint {
  display: block; font-size: var(--t-sm);
  font-weight: 300;
  color: var(--c-text-2);
  margin-top: 3%;
}
</style>