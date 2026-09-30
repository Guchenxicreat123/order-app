<template>
  <view class="edit-dish-page page-bg-ethereal">
    <CustomNav title="编辑菜单" tone="glass" />

    <view class="header">
      <text class="sub">{{ dirtyCount }} 项变更</text>
    </view>

    <!-- 添加菜品：放在画面顶部；点击后弹出编辑窗，编完在下方列表里显示一览 -->
    <view class="add-btn-wrap">
      <button class="add-btn btn-ghost pressable" @tap="addNew">
        <Icon name="plus" size="28rpx" tone="primary" /> 添加菜品
      </button>
    </view>

    <view class="mode-tip" v-if="hasDirty">
      <text class="mt-icon"><Icon name="edit" size="24rpx" tone="primary" /></text>
      <text class="mt-text">已进入编辑模式，所做的修改会在点「保存」时统一提交</text>
    </view>

    <!-- 公共参考菜单入口 -->
    <view class="public-banner pressable" @tap="goPublicMenu">
      <Icon name="home" size="32rpx" tone="primary" />
      <view class="pb-text">
        <text class="pb-title">公共参考菜单</text>
        <text class="pb-sub">浏览平台菜品，一键加入家庭菜单</text>
      </view>
      <text class="pb-arrow">›</text>
    </view>

    <view v-if="error" class="error-state card anim-fade-up">
      <text class="error-title">加载失败</text>
      <text class="error-hint">检查网络后点击重试</text>
      <button class="btn-ghost pressable retry-btn" @tap="loadData">点击重试</button>
    </view>

    <view class="dish-list" v-else-if="!loading">
      <view
        v-for="d in list"
        :key="d._key"
        class="dish-row pressable"
        :class="{ removed: d._deleted }"
        @tap="openEdit(d)"
      >
        <view class="dish-emoji-wrap">
          <text class="dish-emoji">{{ d.imageEmoji || '🍳' }}</text>
        </view>
        <view class="dish-body">
          <text class="dish-name-text">{{ d.name || '未命名菜品' }}</text>
          <view class="dish-meta">
            <text class="meta-pill">{{ catName(d.categoryId) || '未分类' }}</text>
            <text class="meta-pill">{{ spiceText(d.spiceLevel) }}</text>
            <text class="meta-pill" :class="{ off: d.status === 0 }">{{ d.status === 0 ? '已下架' : '在售' }}</text>
          </view>
          <!-- 配方 / 做法摘要 -->
          <view class="recipe-summary" v-if="!d._deleted && (d._ingredients.length > 0 || (d.description && d.description.trim()))">
            <view class="rs-line" v-if="d.description && d.description.trim()">
              <text class="rs-dot"><Icon name="edit" size="28rpx" tone="primary" /></text>
              <text class="rs-text">{{ shortText(d.description, 40) }}</text>
            </view>
            <view class="rs-line" v-if="d._ingredients.length > 0">
              <text class="rs-dot"><Icon name="tomato" size="28rpx" tone="primary" /></text>
              <text class="rs-text">{{ d._ingredients.map(i => `${i.name}${fmtAmount(i)}`).join('、') }}</text>
            </view>
          </view>
        </view>
        <view class="dish-actions">
          <text v-if="d._deleted" class="act undo" @tap.stop="restoreDish(d)">恢复</text>
          <template v-else>
            <text class="act edit" @tap.stop="openEdit(d)">编辑</text>
            <text v-if="d.id" class="act del" @tap.stop="markDel(d)">删除</text>
            <text v-else class="act cancel" @tap.stop="cancelNew(d)">取消</text>
          </template>
        </view>
      </view>

      <view v-if="list.length === 0" class="empty-list">还没有菜品，点上方「添加菜品」新增一道</view>
    </view>

    <view class="bottom-bar safe-bottom">
      <button class="btn-ghost reset-btn pressable" @tap="resetAll">放弃改动</button>
      <TapBurst class="save-burst" :icons="['heart','sparkle']" :count="6">
        <button class="btn-primary save-btn pressable" :loading="saving" :disabled="!hasDirty" @tap="saveAll">保存</button>
      </TapBurst>
    </view>

    <!-- 菜品编辑弹窗：新增/编辑菜品都在这里完成 -->
    <view v-if="editVisible" class="dish-mask" @tap="cancelEdit">
      <view class="dish-dialog" @tap.stop>
        <view class="dd-header">
          <text class="dd-title">{{ isDraft ? '添加菜品' : '编辑菜品' }}</text>
          <text class="dd-close" @tap="cancelEdit"><Icon name="close" size="32rpx" tone="primary" /></text>
        </view>

        <scroll-view scroll-y class="dd-body">
          <!-- 图标 + 名称 -->
          <view class="dd-top">
            <view class="dd-emoji pressable" @tap="pickEmoji(editingDish)">
              <text class="dd-emoji-text">{{ editingDish.imageEmoji || '🍳' }}</text>
              <text class="dd-emoji-hint">换图标</text>
            </view>
            <input
              class="dd-name"
              v-model="editingDish.name"
              @input="markDirty(editingDish)"
              placeholder="菜品名（必填）"
              maxlength="64"
            />
          </view>

          <!-- 分类 / 辣度 / 状态 -->
          <view class="dd-fields">
            <picker
              :value="catIndex(editingDish.categoryId)"
              :range="categories"
              range-key="name"
              @change="(e) => { editingDish.categoryId = categories[e.detail.value].id; markDirty(editingDish); }"
            >
              <view class="dd-field">
                <text class="dd-label">分类</text>
                <text class="dd-value">{{ catName(editingDish.categoryId) || '选分类' }} ›</text>
              </view>
            </picker>
            <picker
              :value="editingDish.spiceLevel || 0"
              :range="spiceOptions"
              range-key="label"
              @change="(e) => { editingDish.spiceLevel = spiceOptions[e.detail.value].value; markDirty(editingDish); }"
            >
              <view class="dd-field">
                <text class="dd-label">辣度</text>
                <text class="dd-value">{{ spiceText(editingDish.spiceLevel) }} ›</text>
              </view>
            </picker>
            <picker
              :value="statusIndex(editingDish.status)"
              :range="statusOptions"
              range-key="label"
              @change="(e) => { editingDish.status = statusOptions[e.detail.value].value; markDirty(editingDish); }"
            >
              <view class="dd-field">
                <text class="dd-label">状态</text>
                <text class="dd-value">{{ editingDish.status === 0 ? '已下架' : '在售' }} ›</text>
              </view>
            </picker>
          </view>

          <!-- 做法 -->
          <view class="rd-section">
            <view class="rd-label"><Icon name="history" size="32rpx" tone="primary" /> 做法 / 步骤</view>
            <textarea
              class="rd-textarea"
              v-model="editingDish.description"
              @input="markDirty(editingDish)"
              placeholder="例如：1. 热锅下油… 2. 大火快炒… 支持多行"
              maxlength="1000"
            />
            <view class="rd-hint">{{ (editingDish.description || '').length }}/1000</view>
          </view>

          <!-- 配方 -->
          <view class="rd-section">
            <view class="rd-label">
              <Icon name="tomato" size="28rpx" tone="primary" /> 配菜配方
              <text class="rd-label-sub">已选 {{ editingDish._ingredients.length }} 项，小计 ¥{{ calcTotal(editingDish._ingredients) }}</text>
            </view>
            <view class="ing-list" v-if="editingDish._ingredients.length > 0">
              <view class="ing-row" v-for="(ing, idx) in editingDish._ingredients" :key="ing.ingId || ing._tmpId">
                <view class="ing-emoji-wrap">
                  <text class="ing-emoji">{{ ing.emoji || '🥔' }}</text>
                </view>
                <text class="ing-name">{{ ing.name }}</text>
                <input class="ing-amount" type="digit" v-model="ing.amount" @input="markDirty(editingDish)" placeholder="用量" />
                <text class="ing-unit">{{ ing.unit || '' }}</text>
                <view class="ing-del pressable" @tap="removeIngredient(idx)">
                  <Icon name="close" size="32rpx" tone="primary" />
                </view>
              </view>
            </view>
            <view v-else class="rd-empty">还没有配菜，从下方添加</view>

            <view class="add-ing-box">
              <view class="add-ing-picker pressable" @tap="openIngredientList">＋ 添加配菜（{{ ingredientOptions.length }} 种可选）</view>
            </view>
          </view>
        </scroll-view>

        <view class="dd-footer">
          <button class="rd-btn cancel" @tap="cancelEdit">取消</button>
          <button class="rd-btn ok" @tap="confirmEdit">完成</button>
        </view>
      </view>
    </view>

    <ConfirmDialog
      :visible="resetDialog.visible"
      title="放弃所有改动？"
      content="放弃后所有未保存的修改（含做法与配方）都会丢失"
      tone="danger"
      icon="close"
      confirm-text="放弃改动"
      @confirm="confirmReset"
      @cancel="resetDialog.visible = false"
    />

    <ConfirmDialog
      :visible="emojiDialog.visible"
      title="选一个图标"
      icon="spoon"
      tone="primary"
      confirm-text="关闭"
      :single-button="true"
      @confirm="emojiDialog.visible = false"
      @cancel="emojiDialog.visible = false"
    >
      <view class="emoji-grid">
        <view
          v-for="opt in emojiOptions"
          :key="opt"
          class="emoji-cell pressable"
          :class="{ active: emojiDialog.target && emojiDialog.target.imageEmoji === opt }"
          @tap="pickEmojiFor(opt)"
        >
          <text class="emoji-cell-text">{{ opt }}</text>
        </view>
      </view>
    </ConfirmDialog>

    <!-- 选配菜一览：替代原生滚动 picker。点一项就加入并关闭。 -->
    <ConfirmDialog
      :visible="ingPickerVisible"
      title="选配菜"
      icon="tomato"
      tone="primary"
      confirm-text="关闭"
      :single-button="true"
      @confirm="ingPickerVisible = false"
      @cancel="ingPickerVisible = false"
    >
      <view class="ing-picker-body">
        <input
          class="ing-picker-search"
          v-model="ingSearch"
          placeholder="搜索配菜"
          placeholder-style="color:#B99AA5;"
        />
        <scroll-view scroll-y class="ing-picker-scroll">
          <view v-if="filteredIngredients.length === 0" class="ing-picker-empty">
            全部已选完 / 没有匹配的配菜
          </view>
          <view
            v-for="opt in filteredIngredients"
            :key="opt.id"
            class="ing-picker-row pressable"
            @tap="pickFromList(opt)"
          >
            <text class="ing-picker-emoji">{{ (allIngredients.find(i => i.id === opt.id) || {}).emoji || '🥔' }}</text>
            <text class="ing-picker-label">{{ opt.label }}</text>
            <text class="ing-picker-arrow">+</text>
          </view>
        </scroll-view>
      </view>
    </ConfirmDialog>
  </view>
</template>

<script setup>
import Icon from '@/components/Icon.vue'
import TapBurst from '@/components/TapBurst.vue'
import CustomNav from '@/components/CustomNav.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getFamilyDishesAll, getCategories, getIngredients, batchSaveDishes } from '../../utils/request.js'

const list = ref([])
const categories = ref([])
const allIngredients = ref([]) // 家庭配菜库（配方可选源）
const saving = ref(false)
const resetDialog = ref({ visible: false })
const loading = ref(false)
const loadingLock = ref(false)
const deletedIds = ref([])

// 菜品编辑弹窗状态（新增/编辑都在弹窗里完成）
const editVisible = ref(false)
const editingDish = ref(null)
const isDraft = ref(false)          // 正在编辑的是「还没进列表」的新菜
const ingredientPickerIndex = ref(-1)

const error = ref(false)
// 用户改了任何"输入类"字段（name/desc/ing.amount/spiceLevel/status/categoryId）就置脏
const markDirty = (d) => { if (d) d._formDirty = true }
const dirtyCount = computed(() => {
  let c = 0
  for (const d of list.value) {
    if (d._deleted || d._isNew || d._recipeDirty || d._nameDirty || d._formDirty) c++
  }
  return c
})
const hasDirty = computed(() => dirtyCount.value > 0 || deletedIds.value.length > 0)

const catName = (id) => {
  const c = categories.value.find(c => c.id === id)
  return c ? c.name : ''
}
const catIndex = (id) => {
  const i = categories.value.findIndex(c => c.id === id)
  return i < 0 ? 0 : i
}
const spiceText = (n) => {
  if (n === 1) return '微辣'
  if (n === 2) return '重辣'
  return '免辣'
}

// picker 选项（用对象数组展示中文标签）
const spiceOptions = [
  { value: 0, label: '免辣', icon: '' },
  { value: 1, label: '微辣', icon: 'sparkle' },
  { value: 2, label: '重辣', icon: 'sparkle' }
]
const statusOptions = [
  { value: 1, label: '在售' },
  { value: 0, label: '已下架' }
]
const statusIndex = (s) => {
  const i = statusOptions.findIndex(o => o.value === (s == null ? 1 : s))
  return i < 0 ? 0 : i
}

// 可添加的配菜 = 家庭配菜库中尚未加入本菜的
const ingredientOptions = computed(() => {
  if (!editingDish.value) return []
  const have = new Set((editingDish.value._ingredients || []).map(i => i.ingId))
  return allIngredients.value
    .filter(i => !have.has(i.id))
    .map(i => ({ id: i.id, label: `${i.name}（${i.unit}）` }))
})

const shortText = (s, n) => (s && s.length > n ? s.slice(0, n) + '…' : s)
const fmtAmount = (i) => {
  if (i.amount === undefined || i.amount === null || i.amount === '') return ''
  return i.unit ? `${i.amount}${i.unit}` : String(i.amount)
}
const calcTotal = (ings) => {
  let t = 0
  for (const i of ings) {
    const p = parseFloat(i.price)
    const a = parseFloat(i.amount)
    if (!isNaN(p) && !isNaN(a)) t += p * a
  }
  return t.toFixed(2)
}

const loadData = async () => {
  if (loadingLock.value) return
  loadingLock.value = true
  error.value = false
  try {
    const [dishesData, cats, ings] = await Promise.all([
      getFamilyDishesAll(),
      getCategories(),
      getIngredients()
    ])
    // 后端新格式：[{ dish: {...}, ingredients: [...] }]
    list.value = (dishesData || []).map(item => {
      const d = item && item.dish ? item.dish : item // 兼容旧格式
      const ings = (item && item.ingredients) || []
      return {
        ...d,
        _key: 'db-' + d.id,
        _isNew: false,
        _deleted: false,
        _nameDirty: false,
        _recipeDirty: false,
        _formDirty: false,
        _ingredients: (ings || []).map(i => ({ ...i })),
        description: d.description || ''
      }
    })
    categories.value = cats || []
    allIngredients.value = ings || []
  } catch (e) {
    error.value = true
    uni.showToast({ title: '加载失败，点击重试', icon: 'none' })
  } finally {
    loadingLock.value = false
    loading.value = false
  }
}

const markRecipeDirty = () => {
  if (editingDish.value) editingDish.value._recipeDirty = true
}

/**
 * 添加菜品：先在弹窗里编好，点「完成」才把它加进列表（一览），
 * 最后点「保存」才真正提交。所以取消不会在列表里留下空行。
 */
const addNew = () => {
  const tmpId = 'tmp-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6)
  const firstCat = categories.value[0]
  editingDish.value = {
    id: null,
    _key: tmpId,
    _isNew: true,
    _deleted: false,
    _nameDirty: false,
    _recipeDirty: false,
    _formDirty: false,
    name: '',
    imageEmoji: '🍳',
    spiceLevel: 0,
    status: 1,
    categoryId: firstCat ? firstCat.id : null,
    familyId: null,
    description: '',
    _ingredients: []
  }
  isDraft.value = true
  ingredientPickerIndex.value = -1
  editVisible.value = true
}

const cancelNew = (d) => { list.value = list.value.filter(i => i._key !== d._key) }
const markDel = (d) => {
  if (!d.id) return
  d._deleted = true
  if (!deletedIds.value.includes(d.id)) deletedIds.value.push(d.id)
}
const restoreDish = (d) => {
  d._deleted = false
  deletedIds.value = deletedIds.value.filter(x => x !== d.id)
}

const emojiDialog = ref({ visible: false, target: null })
const emojiOptions = ['🍳','🥘','🍲','🍜','🍅','🥚','🍗','🍖','🐟','🥩','🥬','🥔','🌶️','🥑','🍋','🧄','🧅','🌽','🥕','🥦','🥛','🧀','🍯','🥄','🍝','🥗','🍛','🦐','🥥','🍆']
const pickEmoji = (d) => {
  emojiDialog.value = { visible: true, target: d }
}
const pickEmojiFor = (opt) => {
  if (emojiDialog.value.target) {
    emojiDialog.value.target.imageEmoji = opt
    emojiDialog.value.target._nameDirty = true
  }
  emojiDialog.value.visible = false
}

// ====== 菜品编辑弹窗 ======
/** 点列表行（或"编辑"）→ 打开弹窗编辑这道菜 */
const openEdit = (d) => {
  if (!d || d._deleted) return
  editingDish.value = d
  isDraft.value = false
  ingredientPickerIndex.value = -1
  editVisible.value = true
}

/** 取消：草稿直接丢弃（没进过列表）；已有菜品的改动已写在对象上，交给"放弃改动"统一处理 */
const cancelEdit = () => {
  editVisible.value = false
  editingDish.value = null
  isDraft.value = false
}

/** 完成：校验 → 草稿此时才进入列表一览（等"保存"统一提交） */
const confirmEdit = () => {
  const d = editingDish.value
  if (!d) return cancelEdit()
  if (!d.name || !d.name.trim()) {
    uni.showToast({ title: '请填写菜品名', icon: 'none' })
    return
  }
  for (const i of (d._ingredients || [])) {
    if (i.amount === '' || i.amount === null || i.amount === undefined) continue
    if (isNaN(parseFloat(i.amount))) {
      uni.showToast({ title: `${d.name} 的用量格式错误`, icon: 'none' })
      return
    }
  }
  if (isDraft.value) {
    d.name = d.name.trim()
    d._nameDirty = true
    list.value.unshift(d)          // 编完了 → 在画面显示一览
    uni.showToast({ title: '已加入列表，点「保存」提交', icon: 'none' })
  }
  editVisible.value = false
  editingDish.value = null
  isDraft.value = false
}
const onPickIngredient = (opt) => {
  if (!opt || !editingDish.value) return
  // 补全信息：从 allIngredients 找原数据
  const src = allIngredients.value.find(i => i.id === opt.id)
  editingDish.value._ingredients.push({
    ingId: opt.id,
    name: src ? src.name : opt.label,
    emoji: src ? src.emoji : '🥔',
    unit: src ? src.unit : '',
    price: src ? src.price : 0,
    amount: '',
    _tmpId: 'tmp-ing-' + Date.now() + '-' + Math.floor(Math.random() * 999)
  })
  markRecipeDirty()
  uni.showToast({ title: `已加入 ${opt.label}`, icon: 'none' })
}

/** 打开「选配菜」一览弹窗（替代旧的滚动 picker） */
const ingPickerVisible = ref(false)
const ingSearch = ref('')
const openIngredientList = () => {
  if (!editingDish.value) return
  ingSearch.value = ''
  ingPickerVisible.value = true
}
/** 过滤后真正展示的配菜（搜中文/拼音就别想了，简单按 name 包含做） */
const filteredIngredients = computed(() => {
  const q = (ingSearch.value || '').trim()
  if (!q) return ingredientOptions.value
  return ingredientOptions.value.filter(o => o.label.indexOf(q) >= 0)
})
const pickFromList = (opt) => {
  onPickIngredient(opt)
  ingPickerVisible.value = false
}
const removeIngredient = (idx) => {
  if (!editingDish.value) return
  editingDish.value._ingredients.splice(idx, 1)
  markRecipeDirty()
}

const goPublicMenu = () => {
  if (hasDirty.value) {
    uni.showModal({
      title: '有未保存的改动',
      content: '去公共参考菜单后返回将丢失未保存的改动，是否继续？',
      confirmText: '继续前往',
      cancelText: '先保存',
      success: (r) => {
        if (r.confirm) uni.navigateTo({ url: '/pages/public-menu/public-menu' })
      }
    })
    return
  }
  uni.navigateTo({ url: '/pages/public-menu/public-menu' })
}

const resetAll = () => {
  if (!hasDirty.value) {
    list.value = []
    deletedIds.value = []
    editVisible.value = false
    editingDish.value = null
    isDraft.value = false
    loadData()
    return
  }
  resetDialog.value.visible = true
}
const confirmReset = () => {
  resetDialog.value.visible = false
  list.value = []
  deletedIds.value = []
  editVisible.value = false
  editingDish.value = null
  isDraft.value = false
  loadData()
}

const saveAll = async () => {
  // 校验：菜品名不能为空、用量必须为合法数字
  const toSave = list.value.filter(d => !d._deleted)
  for (const d of toSave) {
    if (!d.name || !d.name.trim()) {
      uni.showToast({ title: '菜品名不能为空', icon: 'none' })
      return
    }
    for (const i of (d._ingredients || [])) {
      if (i.amount === '' || i.amount === null || i.amount === undefined) continue
      if (isNaN(parseFloat(i.amount))) {
        uni.showToast({ title: `${d.name} 的用量格式错误`, icon: 'none' })
        return
      }
    }
  }
  saving.value = true
  try {
    const dishes = toSave
      .map(d => ({
        id: d.id,
        name: d.name,
        imageEmoji: d.imageEmoji,
        categoryId: d.categoryId,
        description: d.description || '',
        spiceLevel: d.spiceLevel,
        status: d.status,
        ingredients: (d._ingredients || []).map(i => ({
          ingId: i.ingId,
          amount: parseFloat(i.amount) || 0,
          unit: i.unit || ''
        }))
      }))
    const res = await batchSaveDishes({ dishes, deletedIds: deletedIds.value })
    uni.showToast({ title: `已保存（新增${res.created} 更新${res.updated} 删除${res.deleted}）`, icon: 'success' })
    editVisible.value = false
    editingDish.value = null
    isDraft.value = false
    list.value = []
    deletedIds.value = []
    loadData()
  } catch (e) {
    uni.showToast({ title: '保存失败，请稍后重试', icon: 'none' })
  } finally {
    saving.value = false
  }
}

onShow(() => { if (!hasDirty.value) loadData() })
</script>

<style scoped>
/* ============= 甜美少女粉·编辑菜单 ============= */
.edit-dish-page { padding-bottom: 31.25%; }

.header {
  padding: 2% 5% 5%;
  color: var(--c-text);
}
.sub { font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); letter-spacing: 1rpx; }

.error-state { margin: 4%; text-align: center; padding: 12% 6%; }
.error-title { display: block; font-size: var(--t-xl); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.error-hint { display: block; font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); margin-top: 3%; }
.retry-btn { margin-top: 6%; padding: 3% 8%; display: inline-block; font-weight: 400 !important; letter-spacing: 2rpx !important; }

.mode-tip {
  background: rgba(255,240,243,.8);
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
  border: 1rpx solid rgba(218,255,251,.6);
  margin: 3% 4%; padding: 3% 3.5%;
  border-radius: 999rpx; display: flex; align-items: center; gap: 2%;
  box-shadow: var(--glow-soft);
}
.mt-icon { font-size: var(--t-lg); }
.mt-text { font-size: var(--t-sm); font-weight: 400; color: #B4637A; flex: 1; }

.public-banner {
  margin: 3% 4%;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  border-radius: 32rpx;
  padding: 4% 5%;
  display: flex;
  align-items: center;
  gap: 3%;
  box-shadow: var(--glow-soft);
}
.pb-icon { font-size: 150%; }
.pb-text { flex: 1; min-width: 0; }
.pb-title { font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose); display: block; letter-spacing: 1rpx; }
.pb-sub { font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2); display: block; margin-top: 1%; }
.pb-arrow { font-size: 125%; font-weight: 300; color: var(--c-text-3); } /* 点9：.6 仅 2.83:1，提到 .62 */

.dish-list { padding: 3% 4%; display: flex; flex-direction: column; gap: 24rpx; }
.dish-row {
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  border-radius: 32rpx; padding: 4% 5%;
  display: flex; align-items: flex-start; gap: 3%;
  box-shadow: var(--glow-soft);
}
.dish-row.removed { opacity: 0.5; background: rgba(255,255,255,0.35); }
.dish-emoji-wrap {
  width: 84rpx; height: 84rpx; border-radius: 50%;
  background: rgba(255,255,255,0.7);
  border: 2rpx solid rgba(255,255,255,0.55);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}
.dish-emoji { font-size: var(--t-xxl); }
.dish-name-text {
  font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%;
}
.meta-pill {
  font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2);
  background: rgba(255,255,255,0.75);
  border: 1rpx solid rgba(122,74,90,.15);
  border-radius: 999rpx;
  padding: 4rpx 12rpx; margin-right: 2%;
  max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.meta-pill:last-child { margin-right: 0; }
.meta-pill.off { color: #B08; background: rgba(255,228,226,.85); }
.empty-list {
  text-align: center; font-weight: 300; color: var(--c-text-2); font-size: var(--t-sm);
  padding: 14% 6%;
}
.dish-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1%; }
.dish-name {
  font-size: var(--t-md); font-weight: 400; color: var(--c-deep-rose);
  padding: 1% 0; background: transparent;
}
.dish-meta {
  display: flex; align-items: center; gap: 1.5%; flex-wrap: wrap;
  font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2);
  width: 100%;
}
.spice-lab { color: #B4637A; flex-shrink: 0; font-weight: 400; }
.picker {
  font-size: var(--t-xs); font-weight: 400; color: var(--c-primary);
  padding: 1% 3%;
  background: rgba(255,255,255,0.75);
  border: 1rpx solid rgba(255,123,148,.25);
  border-radius: 999rpx;
  white-space: nowrap;
  flex-shrink: 1;
  min-width: 0;
}
.meta-sep { color: var(--c-text-3); flex-shrink: 0; }
.recipe-btn {
  font-size: 20rpx; font-weight: 400; color: var(--c-primary);
  background: rgba(255,255,255,0.75);
  border: 1rpx solid rgba(255,123,148,.25);
  border-radius: 999rpx; padding: 4rpx 14rpx;
  white-space: nowrap; flex-shrink: 0;
  margin-left: auto;
}
.recipe-btn.dirty { background: rgba(237,255,255,.9); color: var(--c-primary); }

.recipe-summary {
  display: flex; flex-direction: column; gap: 0.5%;
  margin-top: 1%; padding: 1.5% 2%;
  background: rgba(255,255,255,0.5);
  border: 1rpx solid rgba(255,255,255,0.4);
  border-radius: 999rpx;
}
.rs-line { display: flex; align-items: flex-start; gap: 1%; font-size: 62.5%; font-weight: 300; color: var(--c-text-2); }
.rs-dot { flex-shrink: 0; }
.rs-text { flex: 1; word-break: break-all; }

.dish-actions { display: flex; align-items: center; flex-shrink: 0; gap: 8rpx; }
.act {
  font-size: var(--t-sm);
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(255,228,226,.9); color: #E5484D;
  white-space: nowrap;
  text-align: center;
  display: inline-block;
  min-width: 88rpx;
  font-weight: 400;
}
.act.edit, .act.undo { background: rgba(255,255,255,0.8); color: var(--c-primary); border: 1rpx solid rgba(255,123,148,.25); }
.act.cancel { background: rgba(255,255,255,0.7); color: var(--c-text-2); }

.add-btn-wrap { padding: 3% 4%; }
.add-btn.btn-ghost {
  width: 100%; background: rgba(255,255,255,0.5) !important;
  color: var(--c-primary) !important;
  border: 2rpx dashed rgba(255,123,148,.5) !important;
  border-radius: 999rpx !important;
  font-size: var(--t-md); font-weight: 400; padding: 4% 0;
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}
.reset-btn.btn-ghost {
  background: rgba(255,255,255,0.6) !important;
  color: var(--c-text-2) !important;
  border: none !important;
  border-radius: 999rpx !important;
}

.bottom-bar {
  position: fixed; bottom: 0; left: 0; right: 0;
  display: flex; gap: 2%;
  padding: 3% 4%;
  padding-bottom: calc(3% + constant(safe-area-inset-bottom));
  padding-bottom: calc(3% + env(safe-area-inset-bottom));
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(30rpx);
  -webkit-backdrop-filter: blur(30rpx);
  border-top: 1rpx solid rgba(255,255,255,0.5);
  box-shadow: 0 -8rpx 32rpx rgba(255,194,207,.18);
  z-index: 100;
}
.save-burst { display: block; flex: 1; min-width: 0; }
.reset-btn {
  flex: 1; min-width: 0;
  width: 100%;
  display: block;
  box-sizing: border-box;
  background: rgba(255,255,255,0.7); color: var(--c-text-2);
  border-radius: 999rpx;
  padding: 28rpx 0;
  font-size: var(--t-md); font-weight: 400;
  letter-spacing: 2rpx;
  margin: 0;
  line-height: 1.4;
}
.reset-btn::after { border: none !important; }
.save-btn {
  width: 100%; display: block; box-sizing: border-box;
  background: var(--g-primary);
  color: white; border: none; border-radius: 999rpx;
  padding: 28rpx 0;
  font-size: var(--t-md); font-weight: 400;
  letter-spacing: 2rpx;
  box-shadow: var(--glow-pink);
  margin: 0;
  line-height: 1.4;
}
.save-btn::after { border: none !important; }
.save-btn[disabled] { background: rgba(122,74,90,.45); box-shadow: none; opacity: 0.7; }

/* 菜品编辑弹窗（底弹） */
.dish-mask {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  width: 100%; height: 100%;
  box-sizing: border-box;
  z-index: 300;
  background: rgba(122,74,90,0.45);
  display: flex; align-items: flex-end; justify-content: center;
  overflow: hidden;
}
.dish-dialog {
  width: 100%; max-width: 100%; min-width: 0;
  box-sizing: border-box;
  height: 84%;
  background: rgba(255,255,255,0.94);
  backdrop-filter: blur(40rpx);
  -webkit-backdrop-filter: blur(40rpx);
  border-radius: 32rpx 32rpx 0 0;
  display: flex; flex-direction: column;
  overflow: hidden;
}
.dd-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 5% 5% 3.5%; border-bottom: 1rpx solid rgba(122,74,90,.18);
}
.dd-title { font-size: var(--t-lg); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.dd-close { font-size: var(--t-xl); color: var(--c-text-2); padding: 1%; font-weight: 300; }
.dd-body {
  flex: 1; min-height: 0; height: 0;
  width: 100%; box-sizing: border-box;
  overflow: hidden;
  padding: 4% 5% 3%;
}
.dd-top { display: flex; align-items: center; gap: 3%; margin-bottom: 4%; min-width: 0; }
.dd-emoji {
  width: 128rpx; height: 128rpx; border-radius: 32rpx;
  background: rgba(255,255,255,0.7);
  border: 2rpx dashed rgba(255,123,148,.6);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  flex-shrink: 0;
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}
.dd-emoji-text { font-size: 220%; line-height: 1.1; }
.dd-emoji-hint { font-size: 56.25%; font-weight: 300; color: var(--c-primary); margin-top: 2rpx; }
.dd-name {
  flex: 1; min-width: 0; width: 100%;
  box-sizing: border-box;
  height: 88rpx; line-height: 88rpx;
  padding: 0 4%;
  font-size: var(--t-md); font-weight: 400; color: var(--c-deep-rose);
  background: rgba(255,255,255,0.7);
  border: 1rpx solid rgba(122,74,90,.2);
  border-radius: 16rpx;
}
.dd-fields { display: flex; flex-direction: column; gap: 12rpx; margin-bottom: 4%; min-width: 0; }
.dd-fields picker { display: block; width: 100%; box-sizing: border-box; }
.dd-field {
  display: flex; align-items: center; justify-content: space-between;
  gap: 2%;
  box-sizing: border-box; width: 100%;
  background: rgba(255,255,255,0.7);
  border: 1rpx solid rgba(122,74,90,.18);
  border-radius: 16rpx; padding: 3% 4%;
}
.dd-label { font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); flex-shrink: 0; }
.dd-value {
  font-size: var(--t-sm); color: var(--c-deep-rose); font-weight: 400;
  min-width: 0; flex-shrink: 1;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; text-align: right;
}
.dd-footer {
  display: flex; gap: 3%; box-sizing: border-box; width: 100%; flex-shrink: 0;
  padding: 3% 5% calc(3% + constant(safe-area-inset-bottom));
  padding-bottom: calc(3% + env(safe-area-inset-bottom));
  border-top: 1rpx solid rgba(122,74,90,.18);
}


/* 配方抽屉 */
.recipe-mask {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(122,74,90,0.45); z-index: 300;
  display: flex; align-items: flex-end;
}
.recipe-drawer {
  width: 100%; max-height: 82vh;
  background: rgba(255,255,255,0.94);
  backdrop-filter: blur(40rpx);
  -webkit-backdrop-filter: blur(40rpx);
  border-radius: 32rpx 32rpx 0 0;
  display: flex; flex-direction: column;
}
.rd-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 5% 5% 3.5%; border-bottom: 1rpx solid rgba(122,74,90,.18);
}
.rd-title { font-size: var(--t-lg); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.rd-close { font-size: var(--t-xl); color: var(--c-text-2); padding: 1%; font-weight: 300; }
.rd-body { flex: 1; padding: 4% 5%; max-height: 56vh; }
.rd-section { margin-bottom: 5%; }
.rd-label {
  font-size: var(--t-md); font-weight: 500; color: var(--c-deep-rose); margin-bottom: 2%;
  display: flex; align-items: center; justify-content: space-between;
}
.rd-label-sub { font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2); }
.rd-textarea {
  width: 100%; min-height: 160rpx;
  background: rgba(255,255,255,0.7);
  border: 1rpx solid rgba(122,74,90,.2);
  border-radius: 16rpx;
  padding: 4%; font-size: 81.25%; font-weight: 400; color: var(--c-deep-rose);
  box-sizing: border-box;
}
.rd-hint { text-align: right; font-size: 62.5%; font-weight: 300; color: var(--c-text-3); margin-top: 1%; }

.ing-list { display: flex; flex-direction: column; gap: 1.5%; margin-bottom: 3%; }
.ing-row {
  display: flex; align-items: center; gap: 2%;
  background: rgba(255,255,255,0.6);
  border: 1rpx solid rgba(255,255,255,0.5);
  border-radius: 999rpx; padding: 2% 3%;
}
.ing-emoji { font-size: var(--t-lg); flex-shrink: 0; }
.ing-name { flex: 1; font-size: 81.25%; font-weight: 400; color: var(--c-deep-rose); min-width: 0; }
.ing-amount {
  width: 120rpx; height: 56rpx; background: rgba(255,255,255,0.85);
  border: 1rpx solid rgba(122,74,90,.2); border-radius: 12rpx;
  padding: 0 2%; font-size: 81.25%; font-weight: 400; color: var(--c-deep-rose); text-align: center;
}
.ing-unit { font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2); flex-shrink: 0; }
.ing-del { font-size: var(--t-md); color: #E5484D; padding: 1% 1%; flex-shrink: 0; }

.rd-empty { font-size: var(--t-sm); font-weight: 300; color: var(--c-text-3); padding: 4% 0; text-align: center; }
.add-ing-box { margin-top: 1%; }
.add-ing-picker {
  background: rgba(255,240,243,.9); color: #B4637A; font-size: 81.25%; font-weight: 400;
  border: 1rpx solid rgba(218,255,251,.7);
  border-radius: 999rpx; padding: 3% 0; text-align: center;
}
.rd-footer {
  display: flex; gap: 4%;
  padding: 3% 5% 5%; border-top: 1rpx solid rgba(122,74,90,.18);
}
.rd-btn {
  flex: 1; height: 88rpx;
  line-height: 88rpx;
  padding: 0; text-align: center;
  display: flex; align-items: center; justify-content: center;
  border-radius: 999rpx; font-size: 93.75%; font-weight: 400 !important;
  letter-spacing: 2rpx !important;
}
.rd-btn.cancel { background: rgba(255,255,255,0.7); color: var(--c-text-2); }
.rd-btn.ok {
  background: var(--g-primary); color: white; border: none;
  box-shadow: var(--glow-pink);
}

/* emoji 选择器弹窗 */
.emoji-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 3%;
  width: 100%;
  margin: 4% 0;
  max-height: 52vh;
  overflow-y: auto;
}
.emoji-cell {
  width: 100%;
  height: 96rpx;
  background: rgba(255,255,255,0.7);
  border: 2rpx solid rgba(122,74,90,.2);
  border-radius: 16rpx;
  display: flex; align-items: center; justify-content: center;
  box-sizing: border-box;
}
.emoji-cell.active {
  background: rgba(255,123,148,0.15);
  border-color: var(--c-primary);
}
.emoji-cell-text { font-size: 150%; line-height: 1; }

/* 选配菜一览弹窗 */
.ing-picker-body { width: 100%; display: flex; flex-direction: column; gap: 3%; margin: 4% 0 2%; }
.ing-picker-search {
  background: rgba(255,255,255,0.7);
  border: 2rpx solid rgba(122,74,90,.2);
  border-radius: 16rpx; padding: 3% 4%;
  font-size: var(--t-md); font-weight: 400; color: var(--c-deep-rose);
}
.ing-picker-scroll {
  max-height: 52vh; min-height: 320rpx;
}
.ing-picker-empty {
  text-align: center; padding: 10% 6%;
  font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2);
}
.ing-picker-row {
  display: flex; align-items: center; gap: 3%;
  padding: 4% 3%;
  border-bottom: 1rpx solid rgba(122,74,90,.15);
  border-radius: 16rpx;
  margin-bottom: 8rpx;
  background: rgba(255,255,255,0.5);
}
.ing-picker-emoji { font-size: var(--t-xl); flex-shrink: 0; }
.ing-picker-label {
  flex: 1; min-width: 0; font-size: var(--t-md); font-weight: 400; color: var(--c-deep-rose);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.ing-picker-arrow { font-size: var(--t-xl); font-weight: 300; color: var(--c-primary); flex-shrink: 0; }
</style>