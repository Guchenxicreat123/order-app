<template>
  <view class="pub-page page-bg-ethereal">
    <CustomNav title="公共参考菜单" tone="glass" />

    <view class="header">
      <Icon name="home" size="36rpx" tone="white" />
      <view class="banner-text">
        <text class="banner-title">公共参考菜单</text>
        <text class="banner-sub" v-if="currentFamilyName">当前家庭：{{ currentFamilyName }}</text>
        <text class="banner-sub" v-else>请先加入或选择一个家庭</text>
      </view>
    </view>

    <view class="no-family-tip glass-card" v-if="!currentFamilyId && dishes.length > 0">
      <view class="tip-row">
        <Icon name="sparkle" size="32rpx" tone="primary" />
        <text>你还没有加入任何家庭，无法使用此功能</text>
      </view>
      <button class="btn-primary pressable" @tap="goToFamily">前往加入家庭</button>
    </view>

    <view class="selection-bar" v-if="dishes.length > 0">
      <text class="sel-info">
        {{ selected.length === 0 ? '请选择要加入的菜品' : `已选 ${selected.length} 项` }}
      </text>
      <view class="sel-actions pressable" @tap="toggleSelectAll">
        <text class="select-all-btn">{{ isAllSelected ? '取消全选' : '全选' }}</text>
      </view>
    </view>

    <view v-if="loading" class="loading-mask">
      <SkeletonBlock variant="spoon" height="160rpx" width="240rpx" text="加载中…" />
    </view>

    <view class="dish-container" v-else>
      <view v-for="cat in categoryList" :key="cat.id" class="cat-group">
        <view class="cat-header">{{ cat.name }}</view>
        <view
          v-for="dish in getDishesByCat(cat.id)"
          :key="dish.id"
          class="dish-item pressable glass-card"
          :class="{ selected: isSelected(dish.id) }"
          @tap="toggleSelect(dish.id)"
        >
          <view class="dish-check">
            <Icon v-if="isSelected(dish.id)" name="check" size="36rpx" tone="white" />
            <view v-else class="check-empty"></view>
          </view>
          <view class="dish-emoji-wrap">
            <text class="dish-emoji-text">{{ dish.imageEmoji || '🍽️' }}</text>
          </view>
          <view class="dish-info">
            <text class="dish-name">{{ dish.name }}</text>
            <text class="dish-desc" v-if="dish.description">{{ dish.description }}</text>
            <view class="dish-meta">
              <text class="tag tag-pri" v-if="dish.spiceLevel === 1">微辣</text>
              <text class="tag tag-danger" v-else-if="dish.spiceLevel === 2">重辣</text>
              <text class="tag tag-gray" v-else>不辣</text>
              <text class="tag tag-link" @tap.stop="goDetail(dish.id)">查看配方 ›</text>
            </view>
          </view>
        </view>
      </view>

      <view class="empty anim-fade-up glass-card" v-if="loadError">

        <view class="empty-illus"><Icon name="spoon" size="100rpx" tone="primary" class="empty-icon" /></view>
        <text class="empty-text">加载失败，请检查网络</text>
        <button class="btn-primary retry-btn pressable" @tap="loadData">点击重试</button>
      </view>

      <view class="empty anim-fade-up glass-card" v-else-if="dishes.length === 0">

        <view class="empty-illus"><Icon name="spoon" size="100rpx" tone="primary" class="empty-icon" /></view>
        <text class="empty-text">暂无公共菜品</text>
      </view>
    </view>

    <view class="bottom-bar safe-bottom" v-if="selected.length > 0 && currentFamilyId">
      <text class="sel-count">已选 {{ selected.length }} 道菜</text>
      <TapBurst :icons="['heart','home','sparkle']" :count="8">
        <button class="btn-primary join-btn pressable" :loading="joining" @tap="handleJoinFamily">加入家庭菜单</button>
      </TapBurst>
    </view>

    <view class="bottom-bar-disabled safe-bottom" v-if="selected.length > 0 && !currentFamilyId">
      <text class="disabled-tip">请先加入家庭</text>
    </view>
  </view>
</template>

<script setup>
import Icon from '@/components/Icon.vue'
import TapBurst from '@/components/TapBurst.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import CustomNav from '@/components/CustomNav.vue'
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getPublicDishes, getCategories, addPublicDishesToFamily } from '../../utils/request.js'

const dishes = ref([])
const categories = ref([])
const selected = ref([])
const loading = ref(false)
const loadError = ref(false)
const joining = ref(false)
const currentFamilyId = ref(null)
const currentFamilyName = ref('')

const categoryList = computed(() => {
  return categories.value.filter(c => dishes.value.some(d => d.categoryId === c.id))
})

const isAllSelected = computed(() => dishes.value.length > 0 && selected.value.length === dishes.value.length)

function getDishesByCat(catId) {
  return dishes.value.filter(d => d.categoryId === catId)
}

function isSelected(id) {
  return selected.value.includes(id)
}

function toggleSelect(id) {
  const idx = selected.value.indexOf(id)
  if (idx >= 0) selected.value.splice(idx, 1)
  else selected.value.push(id)
  try { uni.vibrateShort({ type: 'light' }) } catch (e) {}
}

function toggleSelectAll() {
  if (isAllSelected.value) selected.value = []
  else selected.value = dishes.value.map(d => d.id)
}

async function handleJoinFamily() {
  if (selected.value.length === 0) return
  if (!currentFamilyId.value) {
    uni.showToast({ title: '请先加入家庭', icon: 'none' })
    return
  }
  joining.value = true
  try {
    const res = await addPublicDishesToFamily(selected.value)
    let msg = `已加入 ${res.addedCount} 道菜`
    if (res.skippedCount > 0) {
      msg += `，${res.skippedCount} 道已存在已过滤`
    }
    try { uni.vibrateShort({ type: 'medium' }) } catch (e) {}
    uni.showToast({ title: msg, icon: 'success' })
    selected.value = []
  } catch (e) {} finally {
    joining.value = false
  }
}

function goToFamily() {
  uni.navigateTo({ url: '/pages/family/family' })
}

function goDetail(dishId) {
  uni.navigateTo({ url: `/pages/public-dish-detail/public-dish-detail?id=${dishId}` })
}

const loadData = async () => {
  if (loading.value) return
  loading.value = true
  loadError.value = false
  try {
    const [dishData, catData] = await Promise.all([
      getPublicDishes(),
      getCategories()
    ])
    dishes.value = dishData || []
    categories.value = catData || []
  } catch (e) {
    loadError.value = true
    dishes.value = []
    categories.value = []
  } finally {
    loading.value = false
  }
}

onShow(async () => {
  const userInfo = uni.getStorageSync('userInfo') || {}
  const families = userInfo.families || []
  const active = families.find(f => f.active) || families[0]
  if (active) {
    currentFamilyId.value = active.familyId
    currentFamilyName.value = active.name || '我的家庭'
  }
  loadData()
})
</script>

<style scoped>
/* ============= 甜美少女粉·公共参考菜单 ============= */
.pub-page { min-height: 100vh; padding-bottom: calc(180rpx + env(safe-area-inset-bottom)); }

.header {
  padding: 2% 5% 5%;
  display: flex; align-items: center; gap: 3%;
  color: var(--c-text);
}
.banner-text { display: flex; flex-direction: column; }
.banner-title { font-size: var(--t-lg); font-weight: 500; color: var(--c-text); letter-spacing: 1rpx; }
.banner-sub { font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2); margin-top: 1%; }

.no-family-tip {
  margin: 3% 4%;
  padding: 4%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3%;
  font-size: var(--t-sm);
  font-weight: 400;
  color: #B4637A;
  background: rgba(255,240,243,0.7);
  border: 1rpx solid rgba(255,240,243,0.7);
  border-radius: 32rpx;
  box-shadow: var(--glow-soft);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
}
.tip-row { display: flex; align-items: center; gap: 1%; }

.selection-bar {
  margin: 2% 4% 0;
  padding: 3% 5%;
  display: flex; justify-content: space-between; align-items: center;
  border-radius: 999rpx;
  background: rgba(255,255,255,0.45);
  backdrop-filter: blur(16rpx);
  -webkit-backdrop-filter: blur(16rpx);
  border: 1rpx solid rgba(255,255,255,0.5);
  font-size: var(--t-sm);
  font-weight: 400;
  color: var(--c-deep-rose);
}
.sel-info { color: var(--c-deep-rose); }
.select-all-btn { color: var(--c-primary); font-weight: 400; letter-spacing: 1rpx; }

.loading-mask { padding: 12% 0; display: flex; flex-direction: column; align-items: center; gap: 3%; }

.dish-container { padding: 3%; }
.cat-group { margin-bottom: 4%; }
.cat-header {
  font-size: var(--t-sm);
  color: var(--c-deep-rose);
  padding: 2% 4%;
  font-weight: 500;
  background: rgba(255,255,255,0.5);
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
  border: 1rpx solid rgba(255,255,255,0.5);
  border-radius: 999rpx;
  margin-bottom: 3%;
  display: inline-block;
  letter-spacing: 1rpx;
}

.dish-item {
  border-radius: 32rpx;
  padding: 3%;
  margin-bottom: 2%;
  display: flex; align-items: center; gap: 3%;
  border: 2rpx solid transparent;
  transition: border-color .2s ease, background .2s ease;
}
.dish-item.selected {
  border-color: rgba(255,194,207,.55);
  background: rgba(255,255,255,0.7);
}
.dish-check {
  width: 40rpx; height: 40rpx;
  display: flex; align-items: center; justify-content: center;
  border-radius: 50%;
  flex-shrink: 0;
}
.dish-item.selected .dish-check {
  background: var(--g-primary);
  box-shadow: var(--glow-pink);
}
.check-empty {
  width: 36rpx; height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid rgba(122,74,90,.3);
  background: rgba(255,255,255,0.7);
  backdrop-filter: blur(8rpx);
  -webkit-backdrop-filter: blur(8rpx);
}
.dish-emoji-wrap {
  width: 80rpx; height: 80rpx;
  background: rgba(255,255,255,0.7);
  border: 2rpx solid rgba(255,255,255,0.55);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}
.dish-emoji-text { font-size: 137.5%; line-height: 1; }
.dish-item.selected .dish-emoji-wrap {
  background: rgba(255,255,255,0.85);
}
.dish-info { flex: 1; min-width: 0; }
.dish-name { font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose); display: block; letter-spacing: 1rpx; }
.dish-desc { font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2); display: block; margin-top: 1%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dish-meta { display: flex; gap: 1%; margin-top: 1%; flex-wrap: wrap; }
.tag { font-size: 20rpx; padding: 4rpx 12rpx; border-radius: 999rpx; white-space: nowrap; font-weight: 400; }
.tag-link {
  background: transparent;
  color: var(--c-primary);
  font-weight: 400;
  padding: 4rpx 8rpx;
  letter-spacing: 1rpx;
}

.empty { text-align: center; padding: 12% 5%; color: var(--c-text-3); border-radius: 32rpx; }
.empty-icon { display: block; margin: 0 auto 4%; }
.empty-text { display: block; font-size: var(--t-md); font-weight: 400; color: var(--c-deep-rose); }
.retry-btn {
  display: inline-block;
  margin-top: 5%;
  height: 76rpx;
  line-height: 76rpx;
  padding: 0 10%;
  font-size: var(--t-md);
  font-weight: 400 !important;
  letter-spacing: 2rpx !important;
}

.bottom-bar {
  position: fixed;
  bottom: 0; left: 0; right: 0;
  height: 120rpx;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 4%;
  padding-bottom: calc(0rpx + env(safe-area-inset-bottom));
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(30rpx);
  -webkit-backdrop-filter: blur(30rpx);
  border-top: 1rpx solid rgba(255,255,255,0.5);
  box-shadow: 0 -8rpx 32rpx rgba(255,194,207,.18);
  z-index: 100;
  box-sizing: border-box;
}
.bottom-bar-disabled {
  position: fixed; bottom: 0; left: 0; right: 0;
  height: 100rpx;
  background: rgba(255,255,255,0.75);
  backdrop-filter: blur(24rpx);
  -webkit-backdrop-filter: blur(24rpx);
  border-top: 1rpx solid rgba(255,255,255,0.5);
  display: flex; align-items: center; justify-content: center;
  z-index: 100;
  padding-bottom: calc(0rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}
.sel-count { font-size: var(--t-md); color: var(--c-deep-rose); font-weight: 500; letter-spacing: 1rpx; }
.disabled-tip { font-size: var(--t-md); font-weight: 300; color: var(--c-text-2); }
/* 必须显式 line-height = height：App.vue 有全局 button{line-height:1.5}，
   配上固定 height 会把文字顶到上半部分（80rpx 高、28rpx 字 → 行盒只有 42rpx），
   看起来就不在按钮正中间。写法与 edit-dishes 的 .rd-btn 一致 */
.join-btn { height: 80rpx; line-height: 80rpx; padding: 0 6%; font-size: var(--t-md); font-weight: 400 !important; letter-spacing: 2rpx !important; }
</style>