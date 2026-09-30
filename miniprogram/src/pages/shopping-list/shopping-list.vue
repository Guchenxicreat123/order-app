<template>
  <view class="page page-bg-ethereal">
    <CustomNav title="买菜清单" tone="glass" />

    <!-- 加载中 -->
    <view v-if="loading" class="loading-mask">
      <SkeletonBlock variant="spoon" height="160rpx" width="240rpx" text="掌勺中…" />
    </view>

    <template v-else>
      <!-- 汇总 -->
      <view class="summary glass-card" v-if="list.length > 0">
        <view class="sum-row">
          <text class="sum-label">预计花费</text>
          <text class="sum-value">¥{{ totalCost.toFixed(2) }}</text>
        </view>
        <view class="sum-row">
          <text class="sum-label">配菜种数</text>
          <text class="sum-value">{{ list.length }}</text>
        </view>
        <view class="sum-row">
          <text class="sum-label">日期</text>
          <text class="sum-value date">{{ dateStr }}</text>
        </view>
      </view>

      <!-- 空状态 -->
      <view v-if="error" class="empty glass-card anim-fade-up">

        <view class="empty-illus"><Icon name="heart-egg" size="120rpx" tone="primary" class="empty-icon" /></view>
        <text class="empty-title">加载失败</text>
        <text class="empty-hint">检查网络后点击重试</text>
        <button class="btn-primary back-home pressable" @tap="loadData">点击重试</button>
      </view>

      <view v-else-if="list.length === 0" class="empty glass-card anim-fade-up">

        <view class="empty-illus"><Icon name="heart-egg" size="120rpx" tone="primary" class="empty-icon" /></view>
        <text class="empty-title">今晚还没有已下单的菜</text>
        <text class="empty-hint">等家人下完单，配菜用量会自动聚到这里</text>
        <button class="btn-primary back-home pressable" @tap="goHome">回到首页</button>
      </view>

      <!-- 清单列表 -->
      <view class="list" v-else>
        <view
          v-for="(item, i) in list"
          :key="item.ingId"
          class="ing-row glass-card"
          :class="{ checked: checked[i] }"
          @tap="toggle(i)"
        >
          <view class="check">
            <Icon v-if="checked[i]" name="check" size="36rpx" tone="primary" />
            <view v-else class="check-empty" />
          </view>
          <view class="ing-emoji-wrap">
            <text class="ing-emoji">{{ item.emoji || '🥔' }}</text>
          </view>
          <view class="ing-info">
            <text class="ing-name">{{ item.name }}</text>
            <view class="ing-meta-row">
              <text class="ing-meta">{{ item.totalAmount }}{{ item.unit }}</text>
              <text class="ing-meta">约 ¥{{ item.totalCost.toFixed(2) }}</text>
            </view>
            <text class="ing-source" v-if="item.fromDishes && item.fromDishes.length">
              来自：{{ uniqueDishes(item.fromDishes).join('、') }}
            </text>
          </view>
        </view>
      </view>

      <!-- 重置按钮 -->
      <view class="footer" v-if="list.length > 0">
        <button class="btn-ghost reset-btn pressable" @tap="resetCheck">重置勾选</button>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getShoppingList } from '../../utils/request.js'
import Icon from '../../components/Icon.vue'
import SkeletonBlock from '../../components/SkeletonBlock.vue'
import CustomNav from '../../components/CustomNav.vue'

const loading = ref(true)
const loadingLock = ref(false)
const error = ref(false)
const list = ref([])
const checked = ref([])
const today = new Date()
const dateStr = `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`

function pad(n) { return n < 10 ? '0' + n : '' + n }

// 勾选状态持久化：按 ingId 存，不按下标；并按 family+date 隔离，避免不同家庭/日期串号
const storageKey = () => {
  const info = uni.getStorageSync('userInfo') || {}
  const fam = (info.families || []).find(f => f.active)
    || (info.families || [])[0]
  return `shopChecked:${fam ? fam.familyId : 'none'}:${dateStr}`
}

function loadCheckedSet() {
  try {
    const raw = uni.getStorageSync(storageKey())
    if (!raw) return new Set()
    return new Set(Array.isArray(raw) ? raw : [])
  } catch (e) { return new Set() }
}

function saveCheckedSet(set) {
  try {
    uni.setStorageSync(storageKey(), Array.from(set))
  } catch (e) {}
}

const totalCost = computed(() =>
  list.value.reduce((s, i) => s + parseFloat(i.totalCost || 0), 0)
)

function uniqueDishes(arr) {
  return Array.from(new Set(arr))
}

function toggle(i) {
  const item = list.value[i]
  if (!item || item.ingId == null) return
  const set = loadCheckedSet()
  if (set.has(item.ingId)) set.delete(item.ingId); else set.add(item.ingId)
  saveCheckedSet(set)
  // 触发响应式刷新
  checked.value = list.value.map(it => set.has(it.ingId))
  try { uni.vibrateShort({ type: 'light' }) } catch (e) {}
}

function resetCheck() {
  saveCheckedSet(new Set())
  checked.value = list.value.map(() => false)
  try { uni.showToast({ title: '已重置', icon: 'none' }) } catch (e) {}
}

const loadData = async () => {
  if (loadingLock.value) return
  loadingLock.value = true
  error.value = false
  try {
    const data = await getShoppingList()
    list.value = Object.values(data || {})
    // 只保留「清单里还在」的 ingId；清单里新增的默认未勾选
    const validIds = new Set(list.value.map(it => it.ingId).filter(v => v != null))
    const stored = loadCheckedSet()
    const merged = new Set([...stored].filter(id => validIds.has(id)))
    saveCheckedSet(merged)
    checked.value = list.value.map(it => merged.has(it.ingId))
  } catch (e) {
    list.value = []
    checked.value = []
    error.value = true
  } finally {
    loadingLock.value = false
    loading.value = false
  }
}

const goHome = () => uni.switchTab({ url: '/pages/index/index' })

onShow(loadData)
</script>

<style scoped>
/* ============= 甜美少女粉·买菜清单 ============= */
.page { padding-bottom: 31.25%; }

.loading-mask {
  padding: 15% 0;
  display: flex; flex-direction: column; align-items: center; gap: 3%;
}

.summary {
  margin: 4%;
  padding: 5%;
  border-radius: 32rpx;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  box-shadow: var(--glow-soft);
}
.sum-row {
  display: flex; justify-content: space-between;
  padding: 1.5% 0;
}
.sum-label { font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); }
.sum-value { font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.sum-value.date { font-family: monospace; color: var(--c-deep-rose); font-weight: 400; }

.empty {
  margin: 4%;
  text-align: center;
  padding: 16% 7%;
  border-radius: 32rpx;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  box-shadow: var(--glow-soft);
}
.empty-icon { display: block; margin: 0 auto 4%; }
.empty-title { display: block; font-size: var(--t-lg); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.empty-hint { display: block; font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); margin-top: 2%; }
.back-home { margin-top: 7%; padding: 3% 10%; font-size: var(--t-md); display: inline-block; font-weight: 400 !important; letter-spacing: 2rpx !important; }

.list { padding: 0 4%; }
.ing-row {
  display: flex; align-items: center; gap: 3%;
  padding: 4% 5%; margin-bottom: 16rpx;
  border-radius: 32rpx;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  box-shadow: var(--glow-soft);
  transition: opacity .2s ease;
}
.ing-row.checked {
  border-color: var(--c-success);
  background: rgba(227,247,238,.55);
  opacity: 0.75;
}
.check {
  flex-shrink: 0;
  width: 40rpx; height: 40rpx;
  display: flex; align-items: center; justify-content: center;
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
.ing-emoji { font-size: var(--t-xxl); line-height: 1; }
.ing-info { flex: 1; min-width: 0; }
.ing-name { font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose); display: block; letter-spacing: 1rpx; }
.ing-meta-row { display: flex; gap: 3%; margin-top: 1%; }
.ing-meta { font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2); }
.ing-source { font-size: 62.5%; font-weight: 300; color: var(--c-text-2); display: block; margin-top: 1%; }

.footer { padding: 5% 4%; }
.reset-btn { width: 100%; padding: 28rpx 0; font-size: var(--t-md); font-weight: 400 !important; letter-spacing: 2rpx !important; }
</style>
