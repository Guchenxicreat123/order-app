<template>
  <view class="detail-page page-bg-ethereal" v-if="dish">
    <CustomNav title="菜品详情" tone="glass" />

    <!-- 菜品大图 -->
    <view class="hero-area">
      <view class="hero-icon anim-breath">
        <text class="hero-emoji">{{ dish.imageEmoji || '🍽️' }}</text>
      </view>
      <view class="hero-glow"></view>
    </view>

    <!-- 基本信息 -->
    <view class="info-card glass-card-2">
      <view class="dish-name-row">
        <text class="dish-name">{{ dish.name }}</text>
        <text class="dish-price">¥{{ dish.price }}</text>
      </view>
      <view class="dish-tags">
        <view class="tag" :class="'tag-' + (dish.spiceLevel || 0)">
          <view class="dot"></view>
          {{ dish.spiceLevel === 1 ? '微辣' : dish.spiceLevel === 2 ? '重辣' : '不辣' }}
        </view>
        <view class="tag tag-category">{{ categoryName || '未分类' }}</view>
      </view>
      <text class="dish-desc" v-if="dish.description">{{ dish.description }}</text>
    </view>

    <!-- 配菜信息 -->
    <view class="ingredients-card glass-card-2" v-if="ingredients && ingredients.length > 0">
      <view class="card-title">
        <Icon name="tomato" size="28rpx" tone="primary" />
        <text>配菜配方</text>
      </view>
      <view class="ing-table">
        <view class="ing-row header">
          <text>配菜</text><text>用量</text><text>小计</text>
        </view>
        <view class="ing-row" v-for="ing in ingredients" :key="ing.ingId">
          <text>{{ ing.name }}</text>
          <text>{{ ing.amount }}{{ ing.unit }}</text>
          <text class="price">¥{{ (parseFloat(ing.price) * parseFloat(ing.amount)).toFixed(2) }}</text>
        </view>
      </view>
    </view>

    <!-- 底部操作栏 -->
    <view class="action-bar glass safe-bottom">
      <!-- 管理员操作（OWNER/CHEF） -->
      <view class="admin-actions" v-if="isChef">
        <button class="btn-ghost action-delete pressable" @tap="handleDelete">删除</button>
        <button class="btn-primary action-edit pressable" @tap="showEditDialog">编辑</button>
      </view>
      <template v-else>
        <view class="action-info">
          <text class="action-label">合计</text>
          <text class="action-price">¥{{ dish.price }}</text>
        </view>
        <TapBurst :icons="['spoon','heart','tomato']" :count="8">
          <button class="btn-primary add-btn pressable" @tap="addToMenu">加入购物车</button>
        </TapBurst>
      </template>
    </view>

    <ProductEditDialog
      v-model:visible="editDialogVisible"
      :form="editForm"
      :categories="categories"
      :saving="saving"
      @save="handleSave"
      @category-change="onCategoryChange"
    />

    <ConfirmDialog
      :visible="deleteVisible"
      title="确认删除？"
      :content="`确定要删除「${dish.name}」吗？删除后无法恢复。`"
      tone="danger"
      icon="trash"
      confirm-text="删除"
      @confirm="confirmDelete"
      @cancel="deleteVisible = false"
    />
  </view>

  <!-- 加载骨架 / 失败 -->
  <view v-else class="loading page-bg-ethereal">
    <view v-if="error" class="error-box">

        <view class="empty-illus"><Icon name="heart-egg" size="120rpx" tone="primary" class="empty-icon" /></view>
      <text class="error-title">菜品加载失败</text>
      <text class="error-hint">检查网络后点击重试</text>
      <button class="btn-primary go-btn pressable" @tap="loadDish(currentId)">点击重试</button>
    </view>
    <block v-else>
      <view class="skel-hero">
        <SkeletonBlock variant="circle" height="160rpx" width="160rpx" />
      </view>
      <view class="skel-card">
        <SkeletonBlock height="36rpx" width="50%" />
        <SkeletonBlock height="28rpx" width="80%" />
      </view>
    </block>
  </view>
</template>

<script setup>
import Icon from '@/components/Icon.vue'
import TapBurst from '@/components/TapBurst.vue'
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getDishDetail, getCategories, addMenuItem, updateDish, deleteDish } from '../../utils/request.js'
import { ensureSubscribe, warmPushConfig } from '../../utils/subscribe.js'
import ProductEditDialog from '@/components/ProductEditDialog.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import CustomNav from '@/components/CustomNav.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const dish = ref(null)
const ingredients = ref([])
const categories = ref([])
const categoryName = ref('')
const currentId = ref('')
const isChef = ref(false)
const error = ref(false)
const editDialogVisible = ref(false)
const saving = ref(false)
const deleteVisible = ref(false)
const editForm = ref({ name: '', imageEmoji: '', categoryId: null, categoryName: '', spiceLevel: 0, description: '' })

function initEditForm() {
  const d = dish.value
  const cat = categories.value.find(c => c.id === d.categoryId)
  editForm.value = {
    name: d.name,
    imageEmoji: d.imageEmoji || '',
    categoryId: d.categoryId,
    categoryName: cat ? cat.name : '',
    spiceLevel: d.spiceLevel || 0,
    description: d.description || ''
  }
}

function showEditDialog() {
  initEditForm()
  editDialogVisible.value = true
}

function onCategoryChange(cat) {
  editForm.value.categoryId = cat.id
  editForm.value.categoryName = cat.name
}

function checkChefOrOwner() {
  const userInfo = uni.getStorageSync('userInfo') || {}
  const families = userInfo.families || []
  const fam = families.find(f => f.familyId === dish.value.familyId)
  // 只对主厨(CHEF)开放；家庭创建者(OWNER)只能查看，不能编辑/删除菜品
  isChef.value = fam && fam.role === 'CHEF'
}

const loadDish = async (id) => {
  error.value = false
  try {
    const [detail, cats] = await Promise.all([
      getDishDetail(id),
      getCategories(),
    ])
    dish.value = detail.dish
    ingredients.value = detail.ingredients || []
    categories.value = cats || []
    const cat = cats.find(c => c.id === dish.value.categoryId)
    categoryName.value = cat ? cat.name : ''
    checkChefOrOwner()
  } catch (e) {
    error.value = true
    dish.value = null
  }
}

const addToMenu = async () => {
  if (!dish.value) return
  // 加菜只是进购物车，真正下单在购物车页；这里只预热订阅配置，不弹授权面板打扰用户
  warmPushConfig('MEMBER')
  try {
    await addMenuItem({ dishId: dish.value.id, spiceLevel: dish.value.spiceLevel || 0 })
    try { uni.vibrateShort({ type: 'light' }) } catch (e) {}
    uni.showToast({ title: '已加入购物车', icon: 'success' })
    uni.$emit('cart:changed')
    setTimeout(() => uni.switchTab({ url: '/pages/cart/cart' }), 800)
  } catch (e) {}
}

async function handleSave() {
  if (!editForm.value.name?.trim()) {
    uni.showToast({ title: '请输入菜名', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await updateDish(dish.value.id, {
      name: editForm.value.name,
      imageEmoji: editForm.value.imageEmoji,
      categoryId: editForm.value.categoryId,
      spiceLevel: editForm.value.spiceLevel,
      description: editForm.value.description
    })
    uni.showToast({ title: '保存成功', icon: 'success' })
    editDialogVisible.value = false
    const [detail, cats] = await Promise.all([
      getDishDetail(dish.value.id),
      getCategories()
    ])
    dish.value = detail.dish
    ingredients.value = detail.ingredients || []
    categories.value = cats || []
    const cat = cats.find(c => c.id === dish.value.categoryId)
    categoryName.value = cat ? cat.name : ''
  } catch (e) {} finally {
    saving.value = false
  }
}

function handleDelete() {
  deleteVisible.value = true
}

function confirmDelete() {
  deleteVisible.value = false
  if (!dish.value) return
  deleteDish(dish.value.id)
    .then(() => {
      uni.showToast({ title: '已删除', icon: 'success' })
      setTimeout(() => uni.navigateBack(), 1000)
    })
    .catch(() => {})
}

onLoad((opts) => {
  currentId.value = String(opts.id || '')
  if (opts.id) loadDish(String(opts.id))
})
</script>

<style scoped>
/* ============= 甜美少女粉·菜品详情 ============= */
.detail-page { padding-bottom: 18%; }

/* 头部英雄区：毛玻璃大圆形 + 弥散光 */
.hero-area {
  height: 360rpx;
  display: flex; align-items: center; justify-content: center;
  position: relative; overflow: hidden;
}
.hero-icon {
  width: 220rpx; height: 220rpx;
  background: rgba(255,255,255,0.55);
  border: 2rpx solid rgba(255,255,255,0.7);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  position: relative; z-index: 1;
  backdrop-filter: blur(24rpx);
  -webkit-backdrop-filter: blur(24rpx);
  box-shadow: var(--glow-deep);
}
.hero-emoji { font-size: 110rpx; line-height: 1; }
.hero-glow {
  position: absolute; width: 360rpx; height: 360rpx;
  background: radial-gradient(circle, rgba(255,255,255,.35) 0%, rgba(255,255,255,0) 70%);
  border-radius: 50%;
  top: 50%; left: 50%; transform: translate(-50%, -50%);
  animation: glow-pulse 3s ease-in-out infinite;
  filter: blur(20rpx);
}
@keyframes glow-pulse {
  0%, 100% { transform: translate(-50%, -50%) scale(1); opacity: 0.55; }
  50%      { transform: translate(-50%, -50%) scale(1.3); opacity: 0.3; }
}

/* 通用卡：毛玻璃 */
.info-card,
.ingredients-card {
  margin: 4%;
  padding: 5%;
  border-radius: 32rpx;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  box-shadow: var(--glow-soft);
}
.info-card { margin-top: -5%; position: relative; z-index: 2; }

.dish-name-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 3%; }
.dish-name { font-size: var(--t-xl); font-weight: 500; color: var(--c-deep-rose); flex: 1; letter-spacing: 1rpx; }
.dish-price { font-size: var(--t-xl); font-weight: 500; color: var(--c-deep-rose); margin-left: 3%; letter-spacing: 1rpx; }
.dish-tags { display: flex; gap: 2%; margin-bottom: 3%; flex-wrap: wrap; }
.tag {
  font-size: 20rpx; padding: 4rpx 14rpx; border-radius: 999rpx;
  white-space: nowrap;
  display: inline-flex; align-items: center; gap: 1%;
  font-weight: 400;
}
.tag .dot {
  width: 8rpx; height: 8rpx;
  border-radius: 50%;
  background: currentColor;
}
.tag-0 { background: var(--c-chef-light); color: var(--c-chef); }
.tag-1 { background: #FFF0F3; color: #B4637A; }
.tag-2 { background: var(--c-danger-bg); color: var(--c-danger); }
.tag-category { background: rgba(255,123,148,.15); color: var(--c-primary-dark); }
.dish-desc { display: block; font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); line-height: 1.7; margin-top: 1.5%; }

.card-title {
  display: flex; align-items: center; gap: 2%;
  font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose);
  letter-spacing: 1rpx;
  margin-bottom: 3%;
}
.ing-table { width: 100%; }
.ing-row {
  display: flex; padding: 3% 0;
  border-bottom: 1rpx solid rgba(122,74,90,.15);
  font-size: var(--t-sm); font-weight: 400;
  color: var(--c-deep-rose);
}
.ing-row text { flex: 1; }
.ing-row.header {
  font-weight: 500; font-size: var(--t-xs);
  color: var(--c-text-2);
}
.ing-row .price { color: var(--c-primary); font-weight: 500; }

/* 底部操作栏：毛玻璃 */
.action-bar {
  position: fixed; bottom: 0; left: 0; right: 0; height: 120rpx;
  display: flex; align-items: center; justify-content: space-between; gap: 3%;
  padding: 0 5%;
  border-top: 1rpx solid rgba(255,255,255,0.5);
  z-index: 100;
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(30rpx);
  -webkit-backdrop-filter: blur(30rpx);
  box-shadow: 0 -8rpx 32rpx rgba(255,194,207,.18);
}
.admin-actions { display: flex; gap: 3%; width: 100%; }
.action-info { display: flex; flex-direction: column; flex: 1; }
.action-label { font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2); }
.action-price { font-size: var(--t-xl); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.add-btn {
  height: 80rpx; padding: 0 8%;
  font-size: var(--t-md); font-weight: 400 !important;
  letter-spacing: 2rpx !important;
}
.action-edit, .action-delete {
  flex: 1; height: 80rpx;
  font-size: var(--t-md); font-weight: 400 !important;
  letter-spacing: 2rpx !important;
}

.loading { min-height: 100vh; display: flex; flex-direction: column; }
.error-box {
  flex: 1;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  text-align: center;
  padding: 20% 8%;
}
.error-box .go-btn { margin-top: 6%; padding: 3% 10%; font-weight: 400 !important; letter-spacing: 2rpx !important; }
.error-title { font-size: var(--t-xl); font-weight: 500; color: var(--c-deep-rose); margin-top: 4%; letter-spacing: 1rpx; }
.error-hint { font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); margin-top: 2%; }
.skel-hero {
  height: 360rpx;
  display: flex; align-items: center; justify-content: center;
  background: transparent;
}
.skel-card {
  margin: 4%; padding: 4%;
  border-radius: 32rpx;
  background: rgba(255,255,255,0.4);
  border: 1rpx solid rgba(255,255,255,0.4);
  display: flex; flex-direction: column; gap: 3%;
}
</style>