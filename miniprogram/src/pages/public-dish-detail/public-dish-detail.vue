<template>
  <view class="pdd-page page-bg-ethereal">
    <CustomNav :title="dish?.name || '菜品详情'" tone="glass" />

    <view v-if="loading" class="loading">
      <SkeletonBlock variant="spoon" height="120rpx" width="240rpx" text="加载中…" />
    </view>

    <view v-else-if="!dish" class="empty glass-card">
      <text class="empty-text">{{ loadError ? '加载失败，请检查网络' : '菜品不存在或已下架' }}</text>
      <button class="btn-primary pressable" @tap="loadError ? loadDish() : goBack()">
        {{ loadError ? '重试' : '返回' }}
      </button>
    </view>

    <view v-else class="dish-detail">
      <!-- 顶部菜牌 -->
      <view class="header-card glass-card">
        <view class="emoji-wrap">
          <text class="emoji-text">{{ dish.imageEmoji || '🍽️' }}</text>
        </view>
        <view class="header-info">
          <text class="dish-name">{{ dish.name }}</text>
          <view class="meta-row">
            <text class="tag tag-pri" v-if="dish.spiceLevel === 1">🌶️ 微辣</text>
            <text class="tag tag-danger" v-else-if="dish.spiceLevel === 2">🌶️🌶️ 重辣</text>
            <text class="tag tag-gray" v-else>不辣</text>
          </view>
        </view>
      </view>

      <!-- 描述 -->
      <view class="desc-card glass-card" v-if="dish.description">
        <text class="desc-text">{{ dish.description }}</text>
      </view>

      <!-- 配方 -->
      <view class="recipe-card glass-card">
        <view class="recipe-header">
          <text class="recipe-title">📋 配方</text>
          <text class="recipe-count">{{ (dish.ingredients || []).length }} 项</text>
        </view>

        <view v-if="(dish.ingredients || []).length === 0" class="no-recipe">
          <text>暂无配方数据</text>
        </view>

        <view v-else class="ing-list">
          <view
            v-for="(ing, idx) in dish.ingredients"
            :key="ing.id || idx"
            class="ing-row"
          >
            <text class="ing-emoji">{{ ing.ingEmoji || '🍴' }}</text>
            <view class="ing-info">
              <text class="ing-name">{{ ing.ingName }}</text>
              <text class="ing-cat" v-if="ing.ingCategoryName">{{ ing.ingCategoryName }}</text>
            </view>
            <view class="ing-amount">
              <text class="amount-num">{{ ing.amount }}</text>
              <text class="amount-unit">{{ ing.unit }}</text>
            </view>
            <view class="ing-price" v-if="ing.unitPrice">
              <text class="price-text">¥{{ formatPrice(ing.unitPrice) }}/{{ ing.unit }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 成本卡（粘性）-->
      <view class="cost-bar safe-bottom">
        <view class="cost-info">
          <text class="cost-label">参考成本</text>
          <view class="cost-value-row">
            <text class="cost-value">{{ costInfo.display }}</text>
            <text class="cost-warn" v-if="costInfo.isEstimate && costInfo.warning">{{ costInfo.warning }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import CustomNav from '@/components/CustomNav.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { cachedFetch } from '../../utils/cache.js'
import { getPublicDishesWithIngredients, getPublicDishDetail } from '../../utils/request.js'
import { calcDishCost } from '../../utils/cost.js'

const PUBLIC_DISHES_KEY = 'public_dishes_all_with_ingredients'

const dish = ref(null)
const loading = ref(true)
const loadError = ref(false)

const costInfo = computed(() => calcDishCost(dish.value?.ingredients || []))

function formatPrice(p) {
  const n = Number(p)
  if (isNaN(n)) return '0'
  // 整数或 1 位小数不加 .00
  if (Number.isInteger(n)) return n.toString()
  return n.toFixed(2).replace(/\.?0+$/, '')
}

function goBack() {
  uni.navigateBack()
}

let _currentId = null

async function loadDish() {
  const id = _currentId
  if (!id) {
    loading.value = false
    return
  }
  loading.value = true
  loadError.value = false
  try {
    // 1. 优先从「已预加载的完整列表」里查（命中缓存则 0ms）
    const all = await cachedFetch(
      PUBLIC_DISHES_KEY,
      () => getPublicDishesWithIngredients(),
      30 * 60 * 1000
    )

    let found = (Array.isArray(all) ? all : []).find(d => d.id === id)

    // 2. 缓存里没有则走单点详情接口
    if (!found) {
      found = await getPublicDishDetail(id)
    }

    dish.value = found || null
  } catch (e) {
    console.error('[public-dish-detail] load failed', e)
    dish.value = null
    // 区分两类失败：
    //   - 404（业务码 404）：这道菜真的不存在/已下架 → 不显示"重试"
    //   - 网络失败 / 其它业务码：可重试
    const isNotFound = e && Number(e.code) === 404
    loadError.value = !isNotFound
  } finally {
    loading.value = false
  }
}

onLoad((options) => {
  _currentId = Number(options.id) || null
  loadDish()
})
</script>

<style scoped>
/* ============= 甜美少女粉·公共菜品详情 ============= */
.pdd-page {
  min-height: 100vh;
  padding-bottom: calc(180rpx + env(safe-area-inset-bottom));
}

.loading {
  padding: 15% 5%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4%;
}
.empty {
  margin: 4%;
  padding: 14% 6%;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4%;
}
.empty-text { color: var(--c-deep-rose); font-size: var(--t-md); font-weight: 400; }

.dish-detail { padding: 3% 4%; }

/* 顶部 */
.header-card {
  padding: 5%;
  display: flex;
  align-items: center;
  gap: 4%;
  margin-bottom: 3%;
}
.emoji-wrap {
  width: 120rpx; height: 120rpx;
  background: rgba(255,255,255,0.7);
  border: 2rpx solid rgba(255,255,255,0.55);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
  box-shadow: var(--glow-pink);
}
.emoji-text { font-size: 175%; line-height: 1; }
.header-info { flex: 1; min-width: 0; }
.dish-name {
  font-size: var(--t-xl); font-weight: 500; color: var(--c-deep-rose);
  display: block;
  letter-spacing: 1rpx;
}
.meta-row { display: flex; gap: 2%; margin-top: 2%; flex-wrap: wrap; }

/* 描述 */
.desc-card {
  padding: 4%;
  margin-bottom: 3%;
}
.desc-text {
  font-size: var(--t-sm);
  color: var(--c-text-2);
  font-weight: 300;
  line-height: 1.7;
}

/* 配方 */
.recipe-card {
  padding: 4%;
}
.recipe-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 3%;
  padding-bottom: 2%;
  border-bottom: 1rpx solid rgba(122,74,90,.15);
}
.recipe-title {
  font-size: var(--t-md);
  font-weight: 500;
  color: var(--c-deep-rose);
  letter-spacing: 1rpx;
}
.recipe-count {
  font-size: var(--t-xs);
  font-weight: 300;
  color: var(--c-text-2);
}

.no-recipe {
  padding: 8% 0;
  text-align: center;
  color: var(--c-text-2);
  font-size: var(--t-sm);
  font-weight: 300;
}

.ing-list { }
.ing-row {
  display: flex;
  align-items: center;
  gap: 3%;
  padding: 3% 0;
  border-bottom: 1rpx solid rgba(122,74,90,.15);
}
.ing-row:last-child { border-bottom: none; }

.ing-emoji {
  font-size: 137.5%;
  width: 50rpx;
  text-align: center;
  flex-shrink: 0;
}
.ing-info { flex: 1; min-width: 0; }
.ing-name {
  font-size: var(--t-sm);
  color: var(--c-deep-rose);
  font-weight: 500;
  display: block;
  letter-spacing: 1rpx;
}
.ing-cat {
  font-size: 22rpx;
  font-weight: 300;
  color: var(--c-text-2);
  margin-top: 1%;
  display: block;
}
.ing-amount {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
  flex-shrink: 0;
}
.amount-num {
  font-size: var(--t-md);
  font-weight: 500;
  color: var(--c-primary);
}
.amount-unit {
  font-size: var(--t-xs);
  font-weight: 300;
  color: var(--c-text-2);
}
.ing-price {
  flex-shrink: 0;
  margin-left: 3%;
  text-align: right;
}
.price-text {
  font-size: 22rpx;
  font-weight: 300;
  color: var(--c-text-2);
}

/* 成本 bar */
.cost-bar {
  position: fixed;
  bottom: 0; left: 0; right: 0;
  padding: 3% 5%;
  padding-bottom: calc(3% + env(safe-area-inset-bottom));
  z-index: 100;
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(30rpx);
  -webkit-backdrop-filter: blur(30rpx);
  border-top: 1rpx solid rgba(255,255,255,0.5);
  box-shadow: 0 -8rpx 32rpx rgba(255,194,207,.18);
  box-sizing: border-box;
}
.cost-info { display: flex; flex-direction: column; gap: 1%; }
.cost-label {
  font-size: var(--t-xs);
  font-weight: 300;
  color: var(--c-text-2);
  letter-spacing: 1rpx;
}
.cost-value-row {
  display: flex;
  align-items: baseline;
  gap: 3%;
  flex-wrap: wrap;
}
.cost-value {
  font-size: 140%;
  font-weight: 500;
  color: var(--c-deep-rose);
  letter-spacing: 1rpx;
}
.cost-warn {
  font-size: var(--t-xs);
  font-weight: 300;
  color: var(--c-text-2);
}

/* 复用样式 */
.tag {
  font-size: 22rpx;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  white-space: nowrap;
  font-weight: 400;
}
.tag-pri { background: rgba(255,123,148,.15); color: var(--c-primary-dark); }
.tag-danger { background: rgba(255,231,231,.85); color: #E5484D; }
.tag-gray { background: rgba(240,240,240,.7); color: var(--c-text-2); }

.btn-primary {
  background: var(--g-primary);
  color: white;
  border-radius: 999rpx;
  padding: 2% 5%;
  font-size: var(--t-sm);
  font-weight: 400 !important;
  letter-spacing: 2rpx !important;
  border: none;
  box-shadow: var(--glow-pink);
}
</style>