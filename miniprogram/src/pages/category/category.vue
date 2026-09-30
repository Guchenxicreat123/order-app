<template>
  <view class="category-page page-bg-ethereal">
    <CustomNav title="已有菜单" tone="glass">
      <template #right>
        <view class="cart-btn pressable" @tap="goCart">
          <Icon name="cart" size="32rpx" tone="white" />
          <view class="cart-badge" v-if="cartCount > 0">{{ cartCount }}</view>
        </view>
      </template>
    </CustomNav>

    <!-- 搜索栏 -->
    <view class="search-bar">
      <view class="search-wrap">
        <Icon name="search" size="28rpx" tone="white" />
        <input class="search-input" placeholder="搜索菜品…" v-model="keyword" @input="filterDishes" placeholder-class="ph-white" />
      </view>
    </view>

    <!-- 左侧分类（外层定宽容器，避免自定义组件标签在 flex 中宽度塌缩） -->
    <view class="main">
      <view class="cat-side">
        <CategoryFilter :categories="categories" :active-id="activeCategory" @select="selectCategory" />
      </view>

      <!-- 右侧 -->
      <view class="right">
      <view class="ing-section">
        <view class="section-header pressable" @tap="toggleIngPanel">
          <view class="section-title-row">
            <Icon name="tomato" size="28rpx" tone="primary" />
            <text class="section-title">想自己搭配？</text>
          </view>
          <view class="toggle-arrow">{{ showIngPanel ? '收起' : '展开' }}</view>
        </view>
        <view class="ing-chips" v-if="showIngPanel">
          <view
            v-for="ing in allIngredients.slice(0, 16)"
            :key="ing.id"
            class="ing-chip pressable"
            @tap="goCustomDish"
          >
            {{ ing.name }}
          </view>
          <view class="ing-chip go-custom pressable" @tap="goCustomDish">
            <Icon name="plus" size="22rpx" tone="white" />
            <text>自定义菜品</text>
          </view>
        </view>
      </view>

      <!-- 加载骨架 -->
      <view class="dish-list" v-if="loading && dishes.length === 0">
        <view class="skel-row" v-for="n in 4" :key="n">
          <SkeletonBlock variant="circle" height="80rpx" width="80rpx" />
          <view class="skel-info">
            <SkeletonBlock height="28rpx" width="60%" />
            <SkeletonBlock height="24rpx" width="40%" />
            <SkeletonBlock height="30rpx" width="30%" />
          </view>
        </view>
      </view>

      <!-- 菜品列表 -->
      <view class="dish-list" v-else>
        <ProductCard
          v-for="dish in filteredDishes"
          :key="dish.id"
          :dish="dish"
          @view="viewDish"
          @add="addDish"
        />
        <view class="empty-state anim-fade-up" v-if="loadError">

        <view class="empty-illus"><Icon name="spoon" size="100rpx" tone="primary" class="empty-icon" /></view>
          <text class="empty-title">菜单加载失败</text>
          <text class="empty-hint">检查网络后点击重试</text>
          <button class="btn-primary retry-btn pressable" @tap="loadData">点击重试</button>
        </view>
        <view class="empty-state anim-fade-up" v-else-if="filteredDishes.length === 0">

        <view class="empty-illus"><Icon name="spoon" size="100rpx" tone="primary" class="empty-icon" /></view>
          <text class="empty-title">这一类还没有菜</text>
          <text class="empty-hint">去左侧切换分类，或回首页自定义</text>
        </view>
      </view>
      </view><!-- /.right -->
    </view><!-- /.main -->

    <!-- 浮动下单栏 -->
    <view class="floating-cart glass safe-bottom" v-if="cartCount > 0">
      <view class="cart-badge-icon">
        <Icon name="cart" size="30rpx" tone="white" />
      </view>
      <view class="cart-info2">
        <view class="cart-text">
          <text class="cart-count">{{ cartCount }} 道菜</text>
          <text class="cart-amount">¥{{ cartTotal }}</text>
        </view>
      </view>
      <view class="checkout-wrap"><button class="checkout-btn btn-primary" @tap="goCart">去下单</button></view>
    </view>
  </view>
</template>

<script setup>
import Icon from '@/components/Icon.vue'
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getCategories, getDishes, getIngredients, addToCart, getMyCart } from '../../utils/request.js'
import CategoryFilter from '../../components/CategoryFilter.vue'
import ProductCard from '../../components/ProductCard.vue'
import SkeletonBlock from '../../components/SkeletonBlock.vue'
import CustomNav from '../../components/CustomNav.vue'

const categories = ref([])
const allIngredients = ref([])
const dishes = ref([])
const activeCategory = ref(null)
const keyword = ref('')
const showIngPanel = ref(false)
const cartItems = ref([])
const cartCount = ref(0)
const cartTotal = ref('0.00')
const loading = ref(false)
const loadError = ref(false)
let loadPromise = null

const filteredDishes = computed(() => {
  let list = dishes.value
  if (activeCategory.value) list = list.filter(d => d.categoryId === activeCategory.value)
  if (keyword.value) list = list.filter(d => d.name.includes(keyword.value))
  return list
})

const loadData = async () => {
  if (loadPromise) return loadPromise
  loading.value = true
  loadError.value = false
  loadPromise = (async () => {
    try {
      const [cats, dishList, ings] = await Promise.all([
        getCategories(),
        getDishes(),
        getIngredients(),
      ])
      categories.value = cats || []
      dishes.value = dishList || []
      allIngredients.value = ings || []
      if (cats.length > 0) activeCategory.value = cats[0].id
    } catch (e) {
      loadError.value = true
    } finally {
      loading.value = false
      loadPromise = null
    }
  })()
  return loadPromise
}

const refreshCart = async () => {
  try {
    const items = await getMyCart()
    cartItems.value = items || []
    cartCount.value = (items || []).reduce((s, i) => s + (i.quantity || 1), 0)
    const total = (items || []).reduce((sum, i) => sum + parseFloat(i.price || 0) * (i.quantity || 1), 0)
    cartTotal.value = total.toFixed(2)
  } catch (e) {
    cartCount.value = 0
  }
}

const selectCategory = (id) => { activeCategory.value = id }
const filterDishes = () => {}
const viewDish = (id) => uni.navigateTo({ url: `/pages/product/product?id=${id}` })
const addDish = async (dish) => {
  try {
    await addToCart({ dishId: dish.id, spiceLevel: dish.spiceLevel || 0 })
    try { uni.vibrateShort({ type: 'light' }) } catch (e) {}
    uni.showToast({ title: `「${dish.name}」已加入购物车`, icon: 'none' })
    await refreshCart()
    uni.$emit('cart:changed')
  } catch (e) {}
}
const toggleIngPanel = () => { showIngPanel.value = !showIngPanel.value }
const goCustomDish = () => uni.navigateTo({ url: '/pages/custom/custom' })
const goCart = () => uni.switchTab({ url: '/pages/cart/cart?from=category' })

onShow(() => {
  loadData()
  refreshCart()
})
uni.$on('cart:changed', refreshCart)
</script>

<style scoped>
/* ============= 甜美少女粉·已有菜单（分类筛选） ============= */
.category-page {
  display: flex; flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

.search-bar {
  padding: 2% 4% 4%;
  flex-shrink: 0;
  color: var(--c-text);
}
.search-wrap {
  background: rgba(255,255,255,0.4);
  backdrop-filter: blur(16rpx);
  -webkit-backdrop-filter: blur(16rpx);
  border: 2rpx solid rgba(255,255,255,0.55);
  border-radius: 999rpx;
  padding: 0 4%;
  height: 64rpx;
  display: flex; align-items: center; gap: 2%;
  min-width: 0;
  box-shadow: var(--glow-soft);
}
.search-input {
  flex: 1;
  font-size: var(--t-sm);
  font-weight: 300;
  color: var(--c-text);
  height: 100%;
}
.ph-white { color: var(--c-text-3); }

.cart-btn {
  position: relative;
  width: 64rpx; height: 64rpx;
  display: flex; align-items: center; justify-content: center;
  margin-right: 2%;
}
.cart-badge {
  position: absolute; top: -6rpx; right: -6rpx;
  background: white; color: var(--c-primary);
  font-size: 20rpx; border-radius: 999rpx; padding: 4rpx 12rpx;
  font-weight: 500; min-width: 32rpx; text-align: center;
  white-space: nowrap;
  box-shadow: var(--glow-pink);
}

.main {
  flex: 1; display: flex; overflow: hidden; padding-bottom: 13%;
}
.cat-side {
  width: 120rpx;
  flex-shrink: 0;
  height: 100%;
  display: flex;
  align-items: stretch;
  overflow: hidden;
  margin-right: 12rpx;
}
.right {
  flex: 1; display: flex; flex-direction: column; overflow: hidden;
}

.ing-section {
  padding: 2% 4%;
  background: rgba(255,255,255,0.5);
  backdrop-filter: blur(16rpx);
  -webkit-backdrop-filter: blur(16rpx);
  border: 1rpx solid rgba(255,255,255,0.5);
  border-radius: 24rpx;
  margin: 12rpx;
  box-shadow: var(--glow-soft);
}
.section-header { display: flex; justify-content: space-between; align-items: center; }
.section-title-row { display: flex; align-items: center; gap: 2%; }
.section-title { font-size: var(--t-sm); font-weight: 400; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.toggle-arrow { color: var(--c-primary); font-size: 68.75%; font-weight: 400; }
.ing-chips { display: flex; flex-wrap: wrap; gap: 2%; margin-top: 2%; }
.ing-chip {
  background: rgba(255,255,255,0.6);
  border: 1rpx solid rgba(122,74,90,.2);
  border-radius: 999rpx; padding: 4rpx 16rpx;
  font-size: 22rpx; color: var(--c-deep-rose); font-weight: 400;
  display: flex; align-items: center; gap: 1%;
  white-space: nowrap;
}
.ing-chip.go-custom {
  background: var(--g-primary); color: white; border-color: transparent;
  font-weight: 400; margin-left: auto;
  box-shadow: var(--glow-pink);
}

.dish-list { padding: 16rpx; display: flex; flex-direction: column; flex: 1; overflow-y: auto; }

.skel-row {
  display: flex; align-items: center; gap: 2%;
  background: rgba(255,255,255,0.5);
  border: 1rpx solid rgba(255,255,255,0.5);
  border-radius: 32rpx;
  padding: 3%;
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}
.skel-info { flex: 1; display: flex; flex-direction: column; gap: 1%; }

.empty-state { text-align: center; padding: 11% 5%; color: var(--c-text-3); }
.empty-icon { display: block; margin: 0 auto 3%; }
.empty-title { display: block; font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; margin-top: 2%; }
.empty-hint { display: block; font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); margin-top: 1%; }
.retry-btn {
  display: inline-block;
  margin-top: 4%;
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 10%;
  font-size: var(--t-sm);
  font-weight: 400 !important;
  letter-spacing: 2rpx !important;
}

/* 浮动购物车栏：毛玻璃 */
.floating-cart {
  position: fixed; bottom: 0; left: 0; right: 0;
  padding: 2% 4%;
  display: flex; align-items: center;
  border-top: 1rpx solid rgba(255,255,255,0.5);
  z-index: 100;
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(30rpx);
  -webkit-backdrop-filter: blur(30rpx);
  box-shadow: 0 -8rpx 32rpx rgba(255,194,207,.18);
  box-sizing: border-box;
}
.cart-badge-icon {
  width: 64rpx; height: 64rpx;
  background: var(--g-primary);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  box-shadow: var(--glow-pink);
  flex-shrink: 0;
  margin-right: 3%;
}
.cart-info2 { display: flex; align-items: center; }
.cart-text { display: flex; flex-direction: column; }
.cart-count { font-size: 81.25%; color: var(--c-deep-rose); font-weight: 500; }
.cart-amount { font-size: 68.75%; font-weight: 300; color: var(--c-text-2); }
.checkout-wrap {
  margin-left: auto;
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.checkout-btn {
  width: 120rpx;
  box-sizing: content-box;
  height: 72rpx;
  line-height: 72rpx;
  padding: 0;
  font-size: var(--t-sm);
  font-weight: 400 !important;
  letter-spacing: 2rpx !important;
  text-align: center;
  flex-shrink: 0;
}
</style>