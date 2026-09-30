<template>
  <view class="pub-page page-bg-ethereal">
    <CustomNav title="公共参考配菜" tone="glass" />

    <view class="banner">
      <Icon name="flower" size="40rpx" tone="white" />
      <view class="banner-text">
        <text class="banner-title">公共参考配菜</text>
        <text class="banner-sub" v-if="currentFamilyName">当前家庭：{{ currentFamilyName }}</text>
        <text class="banner-sub" v-else>请先加入或选择一个家庭</text>
      </view>
    </view>

    <view class="no-family-tip glass-card" v-if="!currentFamilyId && ingredients.length > 0">
      <view class="tip-row">
        <Icon name="sparkle" size="32rpx" tone="primary" />
        <text>你还没有加入任何家庭，无法使用此功能</text>
      </view>
      <button class="btn-primary pressable" @tap="goToFamily">前往加入家庭</button>
    </view>

    <view class="selection-bar" v-if="ingredients.length > 0">
      <text class="sel-info">
        {{ selected.length === 0 ? '请选择要加入的配菜' : `已选 ${selected.length} 项` }}
      </text>
      <view class="sel-actions pressable" @tap="toggleSelectAll">
        <text class="select-all-btn">{{ isAllSelected ? '取消全选' : '全选' }}</text>
      </view>
    </view>

    <view v-if="loading" class="loading-mask">
      <SkeletonBlock variant="spoon" height="160rpx" width="240rpx" text="加载中…" />
    </view>

    <view class="ing-container" v-else>
      <view v-for="cat in categoryList" :key="cat.id" class="cat-group">
        <view class="cat-header">{{ cat.name }}</view>
        <view
          v-for="ing in getIngredientsByCat(cat.id)"
          :key="ing.id"
          class="ing-item pressable"
          :class="{ selected: isSelected(ing.id) }"
          @tap="toggleSelect(ing.id)"
        >
          <view class="ing-check">
            <Icon v-if="isSelected(ing.id)" name="check" size="36rpx" tone="white" />
            <view v-else class="check-empty"></view>
          </view>
          <view class="ing-emoji-wrap">
            <text class="ing-emoji-text">{{ ing.emoji || '🥬' }}</text>
          </view>
          <view class="ing-info">
            <text class="ing-name">{{ ing.name }}</text>
            <text class="ing-meta">¥{{ ing.price }}/{{ ing.unit }}</text>
          </view>
        </view>
      </view>

      <view class="empty anim-fade-up" v-if="loadError">

        <view class="empty-illus"><Icon name="tomato" size="100rpx" tone="primary" class="empty-icon" /></view>
        <text class="empty-text">加载失败，请检查网络</text>
        <button class="btn-primary retry-btn pressable" @tap="loadData">点击重试</button>
      </view>

      <view class="empty anim-fade-up" v-else-if="ingredients.length === 0">

        <view class="empty-illus"><Icon name="tomato" size="100rpx" tone="primary" class="empty-icon" /></view>
        <text class="empty-text">暂无公共配菜</text>
      </view>
    </view>

    <view class="bottom-bar safe-bottom" v-if="selected.length > 0 && currentFamilyId">
      <text class="sel-count">已选 {{ selected.length }} 项配菜</text>
      <TapBurst :icons="['heart','tomato','sparkle']" :count="8">
        <button class="btn-primary join-btn pressable" :loading="joining" @tap="handleJoinFamily">加入家庭配菜</button>
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
import { getPublicIngredients, getPublicIngredientCategories, addPublicIngredientsToFamily } from '../../utils/request.js'

const ingredients = ref([])
const categories = ref([])
const selected = ref([])
const loading = ref(false)
const loadError = ref(false)
const joining = ref(false)
const currentFamilyId = ref(null)
const currentFamilyName = ref('')

const categoryList = computed(() => {
  return categories.value.filter(c => ingredients.value.some(i => i.categoryId === c.id))
})

const isAllSelected = computed(() => ingredients.value.length > 0 && selected.value.length === ingredients.value.length)

function getIngredientsByCat(catId) {
  return ingredients.value.filter(i => i.categoryId === catId)
}

function isSelected(id) { return selected.value.includes(id) }

function toggleSelect(id) {
  const idx = selected.value.indexOf(id)
  if (idx >= 0) selected.value.splice(idx, 1)
  else selected.value.push(id)
  try { uni.vibrateShort({ type: 'light' }) } catch (e) {}
}

function toggleSelectAll() {
  if (isAllSelected.value) selected.value = []
  else selected.value = ingredients.value.map(i => i.id)
}

async function handleJoinFamily() {
  if (selected.value.length === 0) return
  if (!currentFamilyId.value) {
    uni.showToast({ title: '请先加入家庭', icon: 'none' })
    return
  }
  joining.value = true
  try {
    const res = await addPublicIngredientsToFamily(selected.value)
    let msg = `已加入 ${res.addedCount} 项配菜`
    if (res.skippedCount > 0) msg += `，${res.skippedCount} 项已存在已过滤`
    try { uni.vibrateShort({ type: 'medium' }) } catch (e) {}
    uni.showToast({ title: msg, icon: 'success' })
    selected.value = []
  } catch (e) {} finally {
    joining.value = false
  }
}

function goToFamily() { uni.navigateTo({ url: '/pages/family/family' }) }

const loadData = async () => {
  if (loading.value) return
  loading.value = true
  loadError.value = false
  try {
    const [ingData, catData] = await Promise.all([
      getPublicIngredients(),
      getPublicIngredientCategories()
    ])
    ingredients.value = ingData || []
    categories.value = catData || []
  } catch (e) {
    loadError.value = true
    ingredients.value = []
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
/* ============= 甜美少女粉·公共参考配菜 ============= */
.pub-page { padding-bottom: 15%; }

.no-family-tip {
  margin: 3% 4%;
  background: rgba(255,240,243,.8);
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
  border: 1rpx solid rgba(218,255,251,.6);
  border-radius: 24rpx;
  padding: 4%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3%;
  font-size: var(--t-sm);
  font-weight: 400;
  color: #B4637A;
  box-shadow: var(--glow-soft);
}
.tip-row { display: flex; align-items: center; gap: 1%; }

.banner {
  padding: 2% 4% 5%;
  display: flex; align-items: center; gap: 3%;
  color: var(--c-text);
}
.banner-text { display: flex; flex-direction: column; }
.banner-title {
  font-size: var(--t-lg); font-weight: 300; color: var(--c-text);
  letter-spacing: 2rpx;
  text-shadow: 0 4rpx 16rpx rgba(255,194,207,.28);
}
.banner-sub { font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2); margin-top: 1%; }

.selection-bar {
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(16rpx);
  -webkit-backdrop-filter: blur(16rpx);
  border-top: 1rpx solid rgba(255,255,255,0.5);
  border-bottom: 1rpx solid rgba(255,255,255,0.5);
  padding: 4% 5%;
  display: flex; justify-content: space-between; align-items: center;
  font-size: var(--t-sm);
}
.sel-info { font-weight: 300; color: var(--c-text-2); }
.select-all-btn { color: var(--c-primary); font-weight: 400; letter-spacing: 1rpx; }

.loading-mask { padding: 12% 0; display: flex; flex-direction: column; align-items: center; gap: 3%; }

.ing-container { padding: 3%; }
.cat-group { margin-bottom: 4%; }
.cat-header {
  font-size: var(--t-sm);
  color: var(--c-deep-rose);
  padding: 1% 3%;
  font-weight: 500;
  letter-spacing: 1rpx;
  background: rgba(255,255,255,0.5);
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
  border: 1rpx solid rgba(255,255,255,0.4);
  border-radius: 999rpx;
  margin-bottom: 2%;
  width: fit-content;
}

.ing-item {
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(16rpx);
  -webkit-backdrop-filter: blur(16rpx);
  border-radius: 32rpx;
  padding: 3% 4%;
  margin-bottom: 12rpx;
  display: flex; align-items: center; gap: 3%;
  box-shadow: var(--glow-soft);
  border: 2rpx solid rgba(255,255,255,0.5);
  transition: border-color .2s ease, background .2s ease;
}
.ing-item.selected {
  border-color: var(--c-primary);
  background: rgba(255,123,148,0.14);
  box-shadow: var(--glow-pink);
}
.ing-check {
  width: 40rpx; height: 40rpx;
  display: flex; align-items: center; justify-content: center;
  border-radius: 50%;
  flex-shrink: 0;
}
.ing-item.selected .ing-check {
  background: var(--g-primary);
}
.check-empty {
  width: 36rpx; height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid rgba(122,74,90,.3);
  background: rgba(255,255,255,0.6);
}
.ing-emoji-wrap {
  width: 84rpx; height: 84rpx;
  background: rgba(255,255,255,0.7);
  border: 2rpx solid rgba(255,255,255,0.55);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}
.ing-emoji-text { font-size: 137.5%; line-height: 1; }
.ing-item.selected .ing-emoji-wrap { background: rgba(255,255,255,0.75); }
.ing-info { flex: 1; min-width: 0; }
.ing-name { font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose); display: block; letter-spacing: 1rpx; }
.ing-meta { font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2); display: block; margin-top: 1%; }

.empty { text-align: center; padding: 15%; color: var(--c-text-3); }
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
  position: fixed; bottom: 0; left: 0; right: 0;
  height: 120rpx;
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(30rpx);
  -webkit-backdrop-filter: blur(30rpx);
  border-top: 1rpx solid rgba(255,255,255,0.5);
  box-shadow: 0 -8rpx 32rpx rgba(255,194,207,.18);
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 4%;
  z-index: 100;
}
.bottom-bar-disabled {
  position: fixed; bottom: 0; left: 0; right: 0;
  height: 100rpx;
  background: rgba(253,248,249,0.9);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border-top: 1rpx solid rgba(255,255,255,0.5);
  display: flex; align-items: center; justify-content: center;
  z-index: 100;
}
.sel-count { font-size: var(--t-md); color: var(--c-deep-rose); font-weight: 500; letter-spacing: 1rpx; }
.disabled-tip { font-size: var(--t-md); font-weight: 300; color: var(--c-text-2); }
.join-btn {
  height: 80rpx; line-height: 80rpx; padding: 0 6%;
  font-size: var(--t-md);
  font-weight: 400 !important;
  letter-spacing: 2rpx !important;
  box-shadow: var(--glow-pink);
}
</style>