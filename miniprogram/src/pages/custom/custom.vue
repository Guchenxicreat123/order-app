<template>
  <view class="custom-page page-bg-ethereal">
    <CustomNav title="自定义菜品" tone="glass" />

    <!-- 顶部说明 -->
    <view class="page-tip">
      <text class="tip-icon"><Icon name="heart-egg" size="32rpx" tone="primary" /></text>
      <view class="tip-info">
        <view class="tip-title">自由组合自定义菜品</view>
        <view class="tip-sub">从配菜库选食材，给它起个名字，下单让主厨帮你做</view>
      </view>
    </view>

    <!-- 配菜分类筛选 -->
    <view class="filter-bar">
      <view class="filter-row">
        <view
          class="filter-chip"
          :class="{ active: activeIngCat === null }"
          @tap="activeIngCat = null"
        >全部</view>
        <view
          v-for="cat in ingCategories"
          :key="cat.id"
          class="filter-chip"
          :class="{ active: activeIngCat === cat.id }"
          @tap="activeIngCat = cat.id"
        >{{ cat.emoji || '' }} {{ cat.name }}</view>
      </view>
    </view>

    <!-- 已选配菜 (固定在顶部) -->
    <view class="selected-bar" v-if="selectedIngs.length > 0">
      <view class="sel-header">
        <text class="sel-title"><Icon name="tomato" size="28rpx" tone="primary" /> 已选（{{ selectedIngs.length }} 种）· 点数量可编辑用量</text>
        <text class="sel-clear" @tap="clearIngredients">清空</text>
      </view>
      <view class="sel-list">
        <view
          v-for="ing in selectedIngs"
          :key="ing.id"
          class="sel-item"
        >
          <view class="si-info" @tap="openAmountEditor(ing)">
            <text class="si-emoji">{{ ing.emoji }}</text>
            <text class="si-name">{{ ing.name }}</text>
            <text class="si-edit">改量</text>
          </view>
          <view class="si-stepper">
            <view class="si-btn minus" @tap="stepAmount(ing, -1)"><Icon name="minus" size="28rpx" tone="primary" /></view>
            <view class="si-amount" @tap="openAmountEditor(ing)">{{ fmtAmount(ing) }}</view>
            <view class="si-btn plus" @tap="stepAmount(ing, 1)">＋</view>
          </view>
          <view class="si-sub">¥{{ linePrice(ing) }}</view>
          <view class="si-del" @tap="removeIng(ing)"><Icon name="close" size="32rpx" tone="primary" /></view>
        </view>
      </view>
      <view class="sel-price-row">
        <view class="sel-price-label">预估价格</view>
        <view class="sel-price">¥{{ customDishEstimate }}</view>
      </view>
    </view>

    <!-- 配菜网格 -->
    <view class="ing-grid">
      <view
        v-for="ing in filteredIngredients"
        :key="ing.id"
        class="ing-tile"
        :class="{ selected: isIngSelected(ing.id) }"
        @tap="toggleIng(ing)"
      >
        <view class="ing-emoji">{{ ing.emoji }}</view>
        <view class="ing-name">{{ ing.name }}</view>
        <view class="ing-price">¥{{ ing.price }}/{{ ing.unit }}</view>
        <view class="ing-check" v-if="isIngSelected(ing.id)"><Icon name="check" size="22rpx" tone="white" /></view>
      </view>
    </view>

    <view v-if="filteredIngredients.length === 0" class="empty-state glass-card">
      <text>该分类暂无配菜</text>
    </view>

    <!-- 用量编辑弹层 -->
    <view v-if="editorVisible" class="editor-mask" @tap="closeAmountEditor">
      <view class="editor-card" @tap.stop>
        <view class="editor-title"><Icon name="tomato" size="28rpx" tone="primary" /> {{ editingIng ? `${editingIng.emoji || ''} ${editingIng.name}` : '' }} · 用量</view>
        <view class="editor-body">
          <view class="ed-row">
            <view class="ed-step" @tap="stepAmount(editingIng, -1)"><Icon name="minus" size="28rpx" tone="primary" /></view>
            <input class="ed-input" type="digit" v-model="editingAmount" placeholder="用量" />
            <view class="ed-step" @tap="stepAmount(editingIng, 1)">＋</view>
          </view>
          <view class="ed-unit-line" v-if="editingIng">
            单位：{{ displayUnit(editingIng) }}
            <template v-if="isWeightUnit(editingIng._baseUnit)">
              （{{ editingIng.name }} ¥{{ editingIng.price }}/{{ editingIng._baseUnit }}，1{{ editingIng._baseUnit }}={{ GRAM_PER_UNIT[editingIng._baseUnit] }}克）
            </template>
            <template v-else>
              （{{ editingIng.name }} 单价 ¥{{ editingIng.price }}/{{ editingIng._baseUnit }}）
            </template>
          </view>
        </view>
        <view class="editor-actions">
          <button class="ed-btn cancel" @tap="closeAmountEditor">取消</button>
          <button class="ed-btn ok" @tap="applyAmount">确定</button>
        </view>
      </view>
    </view>

    <!-- 浮动下单按钮 -->
    <view class="floating-submit safe-bottom" v-if="selectedIngs.length > 0">
      <TapBurst class="submit-burst" :icons="['spoon','heart','tomato']" :count="8">
        <button class="btn-primary submit-btn pressable" @tap="openCustomDishDialog">
          <Icon name="cart" size="32rpx" tone="white" />
          <text>加入购物车 ({{ selectedIngs.length }} 种配菜)</text>
        </button>
      </TapBurst>
    </view>

    <!-- 浮动购物车栏（显示当前购物车菜品数量，点击跳转购物车结算） -->
    <view class="floating-cart glass safe-bottom" v-if="!selectedIngs.length && cartCount > 0" @tap="goCart">
      <view class="cart-info">
        <text class="cart-icon-large"><Icon name="cart" size="36rpx" tone="white" /></text>
        <view class="cart-text">
          <text class="cart-count">{{ cartCount }} 道菜</text>
          <text class="cart-amount">¥{{ cartTotal }}</text>
        </view>
      </view>
      <button class="checkout-btn btn-primary">去结账</button>
    </view>

    <!-- 自定义菜品命名 -->
    <ConfirmDialog
      :visible="nameDialog.visible"
      title="给这道菜起个名字"
      :content="`已选 ${selectedIngs.length} 种配菜`"
      icon="spoon"
      tone="primary"
      confirm-text="加入购物车"
      @confirm="confirmAddCustomDish"
      @cancel="nameDialog.visible = false"
    >
      <input
        class="name-dialog-input"
        v-model="nameDialog.name"
        placeholder="如：番茄鸡蛋面"
        maxlength="20"
        focus
        confirm-type="done"
        @confirm="confirmAddCustomDish"
      />
    </ConfirmDialog>
  </view>
</template>

<script setup>
import Icon from '@/components/Icon.vue'
import TapBurst from '@/components/TapBurst.vue'
import CustomNav from '@/components/CustomNav.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getIngredients, getIngredientCategories, addToCart, getMyCart } from '../../utils/request.js'

const loading = ref(false)
const ingCategories = ref([])
const allIngredients = ref([])
const activeIngCat = ref(null)
const selectedIngs = ref([])
const cartCount = ref(0)
const cartTotal = ref('0.00') // [{id, name, emoji, price, unit, amount}]

// 用量编辑
const editorVisible = ref(false)
const editingIng = ref(null)
const editingAmount = ref('')

// 自定义菜品命名弹窗
const nameDialog = ref({ visible: false, name: '' })

const filteredIngredients = computed(() => {
  let list = allIngredients.value
  if (activeIngCat.value) list = list.filter(i => i.categoryId === activeIngCat.value)
  return list
})

/** 重量类单位 → 每"原单位"折合多少克；非重量单位返回 null */
const GRAM_PER_UNIT = { 斤: 500, 公斤: 1000, 千克: 1000, 两: 50, 克: 1 }
const isWeightUnit = (u) => u != null && GRAM_PER_UNIT[u] != null

/**
 * 显示/编辑单位：重量类统一"克"；非重量类保持原单位
 * 例：土豆(斤) → '克'；鸡蛋(个) → '个'
 */
const displayUnit = (ing) => {
  const u = (ing._baseUnit || ing.unit || '')
  return isWeightUnit(u) ? '克' : u
}

/**
 * 默认用量（选中配菜时）：重量类默认 250 克；非重量类默认 1（勺类 0.5）
 * 例：土豆默认 250（克）；鸡蛋默认 1（个）
 */
const defaultAmount = (ing) => {
  const u = (ing._baseUnit || ing.unit || '')
  if (isWeightUnit(u)) return 250
  if (u === '勺' || u === '两') return 0.5
  return 1
}

/** 用量加减步进：克 50；勺 0.5；其它整类 1 */
const stepOf = (ing) => {
  const u = displayUnit(ing)
  if (u === '克') return 50
  if (u === '勺' || u === '两') return 0.5
  return 1
}

/** 格式化显示用量：土豆 250克 / 鸡蛋 2个（整数不带小数） */
const fmtAmount = (ing) => {
  const a = parseFloat(ing.amount)
  const u = displayUnit(ing)
  if (isNaN(a)) return '0' + u
  const n = Number.isInteger(a) ? String(a) : a.toFixed(1).replace(/\.0$/, '')
  return n + u
}

/**
 * 单行价格（元，按当前显示用量计算）
 * 重量类：单价(每原单位) ÷ 折合克数 × 克数；非重量类：单价 × 数量
 * 例：土豆 ¥3/斤，250克 → 3 ÷ 500 × 250 = ¥1.5
 */
const linePrice = (ing) => {
  const p = parseFloat(ing.price || 0)
  const a = parseFloat(ing.amount)
  if (isNaN(a) || a <= 0) return '0.00'
  const u = (ing._baseUnit || ing.unit || '')
  if (isWeightUnit(u)) {
    const perG = p / GRAM_PER_UNIT[u]  // 每克价
    return (perG * a).toFixed(2)
  }
  return (p * a).toFixed(2)
}

const customDishEstimate = computed(() => {
  let total = 0
  for (const ing of selectedIngs.value) {
    const lp = parseFloat(linePrice(ing))
    if (!isNaN(lp)) total += lp
  }
  return total.toFixed(2)
})

const stepAmount = (ing, dir) => {
  // 若在弹层中编辑，改 editingAmount；否则直接改 ing.amount（已选列表的步进）
  if (editingIng.value === ing && editorVisible.value) {
    const step = stepOf(ing)
    const cur = parseFloat(editingAmount.value)
    const base = isNaN(cur) || cur <= 0 ? 0 : cur
    let next = base + dir * step
    if (next < step && dir > 0) next = step // 加号时最小一个步进
    if (next < 0) next = 0 // 减号允许减到 0
    editingAmount.value = String(Math.round(next * 100) / 100)
    return
  }
  const step = stepOf(ing)
  let cur = parseFloat(ing.amount)
  if (isNaN(cur) || cur <= 0) cur = 0
  let next = cur + dir * step
  if (next < step) next = step // 最小一个步进
  ing.amount = Math.round(next * 100) / 100
}

const openAmountEditor = (ing) => {
  editingIng.value = ing
  editingAmount.value = ing.amount === undefined ? '' : String(ing.amount)
  editorVisible.value = true
}

const closeAmountEditor = () => {
  editorVisible.value = false
  editingIng.value = null
  editingAmount.value = ''
}

const applyAmount = () => {
  if (!editingIng.value) return
  const v = parseFloat(editingAmount.value)
  if (isNaN(v) || v <= 0) {
    uni.showToast({ title: '请输入有效用量', icon: 'none' })
    return
  }
  editingIng.value.amount = Math.round(v * 100) / 100
  closeAmountEditor()
}

const isIngSelected = (id) => selectedIngs.value.some(i => i.id === id)

const toggleIng = (ing) => {
  const idx = selectedIngs.value.findIndex(i => i.id === ing.id)
  if (idx >= 0) {
    selectedIngs.value.splice(idx, 1)
  } else {
    // 选中时归一：记录原单位/原单价，内部 amount 统一为"显示单位"数量
    selectedIngs.value.push({
      ...ing,
      _baseUnit: ing.unit || '',
      amount: defaultAmount(ing)
    })
  }
}

const removeIng = (ing) => {
  const idx = selectedIngs.value.findIndex(i => i.id === ing.id)
  if (idx >= 0) selectedIngs.value.splice(idx, 1)
}

const clearIngredients = () => { selectedIngs.value = [] }

const openCustomDishDialog = () => {
  if (selectedIngs.value.length === 0) {
    uni.showToast({ title: '请先选配菜', icon: 'none' })
    return
  }
  // 用前 2 种配菜名拼接出默认名
  const topNames = selectedIngs.value.slice(0, 2).map(i => i.name)
  nameDialog.value = { visible: true, name: topNames.join('配') }
}

const confirmAddCustomDish = async () => {
  const name = (nameDialog.value.name || '').trim() || '自定义菜品'
  nameDialog.value.visible = false
  try { uni.vibrateShort({ type: 'light' }) } catch (e) {}
  await addCustomDish(name)
  uni.$emit('cart:changed')
}

/** 提交前折算：内部克数 → 原单位数量（250克土豆 → 0.5斤）；非重量类原样 */
const toBaseAmount = (ing) => {
  const a = parseFloat(ing.amount)
  if (isNaN(a) || a <= 0) return defaultAmount(ing)
  const u = ing._baseUnit || ''
  const perUnitG = GRAM_PER_UNIT[u]
  if (perUnitG) {
    // 克数 → 原单位数（如 斤）：round 到 3 位避免浮点尾差
    return Math.round((a / perUnitG) * 1000) / 1000
  }
  return a
}

const addCustomDish = async (name) => {
  try {
    const ings = selectedIngs.value.map(ing => ({
      ingId: ing.id,
      // 提交用「配菜原始单位」的数量：后端 price×amount 与买菜清单按原始单位聚合
      amount: toBaseAmount(ing),
      unit: ing._baseUnit || ing.unit || '',
      name: ing.name
    }))
    await addToCart({
      dishId: null,
      dishName: name,
      customIngs: JSON.stringify(ings),
      spiceLevel: 0,
      remark: ''
    })
    uni.showToast({ title: '已加入购物车', icon: 'success' })
    selectedIngs.value = []
    // 不跳转购物车画面，用户继续选菜或从底部 tab 进入购物车结算
    await refreshCart()
  } catch (e) {
    uni.showToast({ title: '加入失败', icon: 'none' })
  }
}

const refreshCart = async () => {
  if (loading.value) return
  try {
    const items = await getMyCart()
    cartCount.value = (items || []).length
    const total = (items || []).reduce((sum, i) => sum + parseFloat(i.price || 0) * (i.quantity || 1), 0)
    cartTotal.value = total.toFixed(2)
  } catch (e) {
    cartCount.value = 0
  }
}

const goCart = () => uni.switchTab({ url: '/pages/cart/cart?from=custom' })

onLoad(async () => {
  try {
    const [ings, cats] = await Promise.all([
      getIngredients(),
      getIngredientCategories()
    ])
    allIngredients.value = ings || []
    ingCategories.value = cats || []
  } catch (e) {}
})

onShow(() => { refreshCart() })
</script>

<style scoped>
/* ============= 甜美少女粉·自定义菜品 ============= */
.custom-page { padding-bottom: calc(220rpx + env(safe-area-inset-bottom)); }

/* 顶部说明：浮在渐变上的文字 */
.page-tip {
  display: flex;
  gap: 4%;
  padding: 4% 5% 5%;
  color: var(--c-text);
  align-items: center;
}
.tip-icon { font-size: 175%; }
.tip-title {
  font-size: 93.75%; font-weight: 300; color: var(--c-text);
  letter-spacing: 2rpx;
  text-shadow: 0 4rpx 16rpx rgba(255,123,148,.22);
}
.tip-sub { font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2); margin-top: 1%; }

/* 筛选条 */
.filter-bar {
  background: rgba(255,255,255,0.5);
  backdrop-filter: blur(16rpx);
  -webkit-backdrop-filter: blur(16rpx);
  border-top: 1rpx solid rgba(255,255,255,0.5);
  border-bottom: 1rpx solid rgba(255,255,255,0.5);
  padding: 2% 3%;
}
.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 1.5% 1%;
}
.filter-chip {
  display: inline-block;
  padding: 1.5% 4%;
  border-radius: 999rpx;
  background: rgba(255,255,255,0.6);
  border: 1rpx solid rgba(122,74,90,.18);
  font-size: var(--t-sm);
  font-weight: 400;
  color: var(--c-deep-rose);
  flex-shrink: 0;
}
.filter-chip.active {
  background: var(--g-primary);
  border-color: transparent;
  color: white;
  font-weight: 400;
  box-shadow: var(--glow-pink);
}

/* 已选配菜 */
.selected-bar {
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  margin: 3% 4%;
  padding: 4% 5%;
  border-radius: 32rpx;
  box-shadow: var(--glow-soft);
  border: 1rpx solid rgba(255,255,255,0.55);
  border-left: 6rpx solid rgba(255,123,148,.5);
  max-height: 400rpx;
  overflow-y: auto;
}
.sel-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 2%; }
.sel-title { font-size: 81.25%; font-weight: 400; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.sel-clear { font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2); }
.sel-list { display: flex; flex-direction: column; gap: 12rpx; }
.sel-item {
  display: flex; align-items: center; gap: 2%;
  background: rgba(255,255,255,0.5);
  border: 1rpx solid rgba(255,255,255,0.5);
  border-radius: 16rpx;
  padding: 2% 2.5%;
}
.si-info { display: flex; align-items: center; gap: 1.5%; flex: 1; min-width: 0; }
.si-emoji { font-size: 93.75%; }
.si-name { font-size: var(--t-sm); color: var(--c-deep-rose); font-weight: 400; min-width: 0; overflow: hidden; white-space: nowrap; }
.si-edit {
  font-size: var(--t-xs); color: var(--c-primary);
  border: 1rpx solid rgba(255,123,148,.5);
  border-radius: 999rpx; padding: 0.5% 2%; flex-shrink: 0; opacity: 0.9;
}
.si-stepper { display: flex; align-items: center; gap: 1%; flex-shrink: 0; }
.si-btn {
  width: 44rpx; height: 44rpx; border-radius: 50%;
  background: rgba(255,255,255,0.8); color: var(--c-primary); font-size: 93.75%;
  display: flex; align-items: center; justify-content: center; font-weight: 400;
}
.si-btn:active { opacity: 0.6; }
.si-amount {
  min-width: 96rpx; text-align: center;
  font-size: 81.25%; font-weight: 500; color: var(--c-deep-rose);
  background: rgba(255,255,255,0.85); border-radius: 12rpx; padding: 1% 1.5%;
}
.si-sub { font-size: var(--t-xs); color: var(--c-deep-rose); font-weight: 500; flex-shrink: 0; min-width: 90rpx; text-align: right; }
.si-del { font-size: 81.25%; color: #E5484D; padding: 1%; flex-shrink: 0; }
.sel-price-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 3%;
  padding-top: 3%;
  border-top: 1rpx solid rgba(122,74,90,.18);
}
.sel-price-label { font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); }
.sel-price { font-size: var(--t-lg); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }

/* 用量编辑弹层 */
.editor-mask {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0; z-index: 300;
  background: rgba(122,74,90,0.45);
  display: flex; align-items: center; justify-content: center;
}
.editor-card {
  width: 580rpx;
  background: rgba(255,255,255,0.9);
  backdrop-filter: blur(40rpx);
  -webkit-backdrop-filter: blur(40rpx);
  border: 1rpx solid rgba(255,255,255,0.7);
  border-radius: 32rpx;
  padding: 6% 5.5% 5%;
  box-shadow: var(--glow-deep);
}
.editor-title { font-size: 93.75%; font-weight: 500; color: var(--c-deep-rose); text-align: center; margin-bottom: 5%; letter-spacing: 1rpx; }
.ed-row { display: flex; align-items: center; justify-content: center; gap: 4%; }
.ed-step {
  width: 72rpx; height: 72rpx; border-radius: 50%;
  background: rgba(255,123,148,0.12); color: var(--c-primary); font-size: 125%;
  display: flex; align-items: center; justify-content: center; font-weight: 400;
}
.ed-input {
  width: 200rpx; height: 80rpx; text-align: center;
  background: rgba(255,255,255,0.7);
  border: 1rpx solid rgba(122,74,90,.25);
  border-radius: 16rpx;
  font-size: var(--t-xl); font-weight: 500; color: var(--c-deep-rose);
}
.ed-unit-line { text-align: center; font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2); margin-top: 3%; }
.editor-actions { display: flex; gap: 4%; margin-top: 5%; }
.ed-btn { flex: 1; height: 84rpx; border-radius: 999rpx; font-size: var(--t-md); font-weight: 400 !important; letter-spacing: 2rpx !important; }
.ed-btn.cancel { background: rgba(255,255,255,0.7); color: var(--c-text-2); }
.ed-btn.ok {
  background: var(--g-primary); color: white; border: none;
  box-shadow: var(--glow-pink);
}

/* 自定义命名弹层输入 */
.name-dialog-input {
  width: 100%;
  margin-top: 2%;
  padding: 4%;
  text-align: center;
  font-size: var(--t-lg);
  font-weight: 400;
  border: 2rpx solid rgba(122,74,90,.25);
  border-radius: 24rpx;
  color: var(--c-deep-rose);
  background: rgba(255,255,255,0.6);
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}

/* 配菜网格 */
.ing-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12rpx 1.5%;
  padding: 2% 3%;
}
.ing-tile {
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(16rpx);
  -webkit-backdrop-filter: blur(16rpx);
  border-radius: 20rpx;
  padding: 2% 1.5%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  border: 2rpx solid rgba(255,255,255,0.45);
  position: relative;
  box-shadow: var(--glow-soft);
}
.ing-tile.selected {
  border-color: rgba(255,123,148,.55);
  background: rgba(255,123,148,0.12);
  box-shadow: var(--glow-pink);
}
.ing-emoji { font-size: 130%; line-height: 1; }
.ing-name {
  font-size: var(--t-xs);
  font-weight: 400;
  color: var(--c-deep-rose);
  margin-top: 1%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
.ing-price {
  font-size: 68.75%;
  font-weight: 300;
  color: var(--c-text-2);
  margin-top: 0.5%;
}
.ing-check {
  position: absolute;
  top: 4rpx;
  right: 4rpx;
  background: var(--g-primary);
  color: white;
  width: 26rpx;
  height: 26rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 56.25%;
  font-weight: 400;
}

/* 空状态 */
.empty-state { text-align: center; padding: 14%; color: var(--c-text-2); font-size: 81.25%; font-weight: 300; }

/* 浮动下单按钮 */
.floating-submit {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 3% 4%;
  padding-bottom: calc(4% + constant(safe-area-inset-bottom));
  padding-bottom: calc(4% + env(safe-area-inset-bottom));
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(30rpx);
  -webkit-backdrop-filter: blur(30rpx);
  border-top: 1rpx solid rgba(255,255,255,0.5);
  box-shadow: 0 -8rpx 32rpx rgba(255,194,207,.18);
  z-index: 100;
}
.submit-burst { display: block; width: 100%; }
.submit-btn {
  width: 100%;
  background: var(--g-primary) !important;
  border: none !important;
  border-radius: 999rpx !important;
  color: white !important;
  font-size: 93.75% !important;
  font-weight: 400 !important;
  letter-spacing: 2rpx !important;
  height: 96rpx !important;
  line-height: 96rpx !important;
  box-shadow: var(--glow-pink) !important;
}

/* 浮动购物车栏（已有菜但没选新菜时显示） */
.floating-cart {
  position: fixed;
  bottom: 0; left: 0; right: 0;
  padding: 3% 4%;
  padding-bottom: calc(4% + constant(safe-area-inset-bottom));
  padding-bottom: calc(4% + env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(30rpx);
  -webkit-backdrop-filter: blur(30rpx);
  border-top: 1rpx solid rgba(255,255,255,0.5);
  box-shadow: 0 -8rpx 32rpx rgba(255,194,207,.18);
  z-index: 99;
}
.cart-info { display: flex; align-items: center; gap: 2%; }
.cart-icon-large { font-size: 175%; }
.cart-text { display: flex; flex-direction: column; }
.cart-count { font-size: 81.25%; color: var(--c-deep-rose); font-weight: 400; }
.cart-amount { font-size: var(--t-xs); color: var(--c-deep-rose); font-weight: 500; }
.checkout-btn {
  background: var(--g-primary) !important;
  border: none !important;
  color: white !important;
  border-radius: 999rpx !important;
  font-size: var(--t-md) !important;
  font-weight: 400 !important;
  letter-spacing: 2rpx !important;
  padding: 3% 7% !important;
  height: auto !important;
  line-height: 1.4 !important;
  box-shadow: var(--glow-pink) !important;
}
</style>