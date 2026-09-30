<template>
  <view class="edit-ing-page page-bg-ethereal">
    <CustomNav title="编辑配菜" tone="glass" />

    <view class="header">
      <text class="sub">{{ dirtyCount }} 项变更</text>
    </view>

    <!-- 分类切换 -->
    <view class="cat-bar">
      <scroll-view scroll-x class="cat-scroll">
        <view
          class="cat-chip"
          :class="{ active: activeCatId === null }"
          @tap="activeCatId = null"
        >全部</view>
        <view
          v-for="c in categories"
          :key="c.id"
          class="cat-chip"
          :class="{ active: activeCatId === c.id }"
          @tap="activeCatId = c.id"
        >{{ c.emoji || '' }} {{ c.name }}</view>
      </scroll-view>
    </view>

    <!-- 公共参考配菜入口 -->
    <view class="public-banner pressable" @tap="goPublicIngredients">
      <Icon name="flower" size="40rpx" tone="primary" />
      <view class="pb-text">
        <text class="pb-title">公共参考配菜</text>
        <text class="pb-sub">浏览平台常用配菜，一键加入家庭</text>
      </view>
      <text class="pb-arrow">›</text>
    </view>

    <!-- 编辑模式提示 -->
    <view class="mode-tip" v-if="hasDirty">
      <text class="mt-icon"><Icon name="edit" size="24rpx" tone="primary" /></text>
      <text class="mt-text">已进入编辑模式，所做的修改会在点「保存」时统一提交</text>
    </view>

    <!-- 新增按钮 -->
    <view class="add-btn-wrap">
      <button class="add-btn btn-ghost pressable" @tap="openAdd"><Icon name="plus" size="28rpx" tone="primary" /> 新增配菜</button>
    </view>

    <!-- 配菜列表 -->
    <view v-if="error" class="error-state card anim-fade-up">
      <text class="error-title">加载失败</text>
      <text class="error-hint">检查网络后点击重试</text>
      <button class="btn-ghost pressable retry-btn" @tap="loadData">点击重试</button>
    </view>

    <view class="ing-list" v-else>
      <view
        v-for="ing in filteredList"
        :key="ing._key"
        class="ing-row"
        :class="{ removed: ing._deleted }"
      >
        <view class="ing-emoji-wrap" @tap="pickEmoji(ing)">
          <text class="ing-emoji">{{ ing.emoji || '🥔' }}</text>
        </view>
        <view class="ing-body">
          <input
            class="ing-name"
            v-model="ing.name"
            @input="markDirty(ing)"
            placeholder="配菜名"
            :disabled="ing._deleted"
          />
          <view class="ing-meta">
            <input
              class="meta-input unit"
              v-model="ing.unit"
              @input="markDirty(ing)"
              placeholder="单位"
              :disabled="ing._deleted"
            />
            <text class="meta-sep">·</text>
            <text class="meta-price">¥</text>
            <input
              class="meta-input price"
              type="digit"
              v-model="ing.price"
              @input="markDirty(ing)"
              placeholder="价格"
              :disabled="ing._deleted"
            />
            <text class="meta-sep">·</text>
            <switch
              :checked="ing.status === 1"
              @change="(e) => { ing.status = e.detail.value ? 1 : 0; markDirty(ing); }"
              :disabled="ing._deleted"
              color="#FF7B94"
              style="transform: scale(0.7);"
            />
            <text class="meta-sep">·</text>
            <picker
              :value="ing.categoryId || categories[0]?.id || 0"
              :range="categories"
              range-key="name"
              @change="(e) => { ing.categoryId = categories[e.detail.value].id; markDirty(ing); }"
              :disabled="ing._deleted"
            >
              <view class="picker">
                {{ catName(ing.categoryId) || '选分类' }}
              </view>
            </picker>
          </view>
        </view>
        <view class="ing-actions">
          <text v-if="ing._deleted" class="act-del undo" @tap="restoreIng(ing)">恢复</text>
          <text v-else-if="ing.id" class="act-del" @tap="markDel(ing)">删除</text>
          <text v-else class="act-del cancel" @tap="cancelNew(ing)">取消</text>
        </view>
      </view>
    </view>

    <!-- 底部保存 -->
    <view class="bottom-bar safe-bottom">
      <button class="btn-ghost reset-btn pressable" @tap="resetAll">放弃改动</button>
      <TapBurst class="save-burst" :icons="['heart','sparkle','tomato']" :count="6">
        <button class="btn-primary save-btn pressable" :loading="saving" :disabled="!hasDirty" @tap="saveAll">保存</button>
      </TapBurst>
    </view>

    <!-- 加载中遮罩 -->
    <view class="loading-mask" v-if="loading">
      <view class="loading-box">
        <view class="spinner"></view>
        <text class="loading-text">加载配菜中…</text>
      </view>
    </view>

    <ConfirmDialog
      :visible="resetDialog.visible"
      title="放弃所有改动？"
      content="放弃后所有未保存的修改都会丢失"
      tone="danger"
      icon="close"
      confirm-text="放弃改动"
      @confirm="confirmReset"
      @cancel="resetDialog.visible = false"
    />

    <ConfirmDialog
      :visible="emojiDialog.visible"
      title="选一个图标"
      icon="flower"
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
          :class="{ active: emojiDialog.target && emojiDialog.target.emoji === opt }"
          @tap="pickEmojiFor(opt)"
        >
          <text class="emoji-cell-text">{{ opt }}</text>
        </view>
      </view>
    </ConfirmDialog>

    <!-- 新增配菜弹窗：填好点「加入」才进列表，跟编辑/删除公用同一份「保存」提交 -->
    <ConfirmDialog
      :visible="addDialog.visible"
      title="新增配菜"
      icon="plus"
      tone="primary"
      confirm-text="加入"
      @confirm="confirmAdd"
      @cancel="addDialog.visible = false"
    >
      <view class="add-form" v-if="addDialog.draft">
        <view class="add-top">
          <view class="add-emoji pressable" @tap="openAddEmoji">
            <text class="add-emoji-text">{{ addDialog.draft.emoji || '🥔' }}</text>
            <text class="add-emoji-hint">换图标</text>
          </view>
          <input
            class="add-name"
            v-model="addDialog.draft.name"
            placeholder="配菜名（必填）"
            maxlength="32"
            :focus="addDialog.visible"
          />
        </view>
        <view class="add-meta">
          <input
            class="add-meta-input unit"
            v-model="addDialog.draft.unit"
            placeholder="单位（克/个）"
            maxlength="8"
          />
          <text class="add-meta-sep">·</text>
          <text class="add-meta-prefix">¥</text>
          <input
            class="add-meta-input price"
            type="digit"
            v-model="addDialog.draft.price"
            placeholder="价格"
          />
          <text class="add-meta-sep">·</text>
          <picker
            :value="addCatIndex(addDialog.draft.categoryId)"
            :range="categories"
            range-key="name"
            @change="(e) => { addDialog.draft.categoryId = categories[e.detail.value].id }"
          >
            <view class="add-cat-picker">{{ catName(addDialog.draft.categoryId) || '选分类' }} ›</view>
          </picker>
        </view>
      </view>
    </ConfirmDialog>
  </view>
</template>

<script setup>
import Icon from '@/components/Icon.vue'
import TapBurst from '@/components/TapBurst.vue'
import CustomNav from '@/components/CustomNav.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { ref, computed, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getIngredients, getIngredientCategories, batchSaveIngredients } from '../../utils/request.js'

const list = ref([])
const categories = ref([])
const activeCatId = ref(null)
const saving = ref(false)
const resetDialog = ref({ visible: false })
const loading = ref(true) // 页面加载遮罩（首次进入显示"加载配菜中…"）
const loadingLock = ref(false) // 防重入锁（与遮罩分离，避免初始 true 挡住首次加载）
const deletedIds = ref([]) // 待删除的 id 列表

const filteredList = computed(() => {
  if (activeCatId.value === null) return list.value
  return list.value.filter(i => i.categoryId === activeCatId.value)
})

const error = ref(false)
const markDirty = (i) => { if (i) i._formDirty = true }
const dirtyCount = computed(() => {
  let c = 0
  for (const i of list.value) {
    if (i._deleted || i._isNew || i._formDirty) c++
  }
  return c
})

const hasDirty = computed(() => dirtyCount.value > 0 || deletedIds.value.length > 0)

const catName = (id) => {
  const c = categories.value.find(c => c.id === id)
  return c ? c.name : ''
}

const loadData = async () => {
  // 避免首次进入两次 onShow 触发重复请求
  if (loadingLock.value) return
  loadingLock.value = true
  error.value = false
  try {
    const [ings, cats] = await Promise.all([
      getIngredients(),
      getIngredientCategories()
    ])
    list.value = (ings || []).map(i => ({
      ...i,
      _key: 'db-' + i.id,
      _isNew: false,
      _deleted: false,
      _formDirty: false
    }))
    categories.value = cats || []
  } catch (e) {
    error.value = true
    uni.showToast({ title: '加载失败，点击重试', icon: 'none' })
  } finally {
    loadingLock.value = false
    loading.value = false
  }
}

const addNew = () => {
  // 兼容老路径：仍允许直接插草稿行（万一有调用方）。新增主入口是 openAdd()。
  const tmpId = 'tmp-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6)
  const firstCat = categories.value[0]
  list.value.unshift({
    id: null,
    _key: tmpId,
    _isNew: true,
    _deleted: false,
    _formDirty: false,
    name: '',
    emoji: '🥔',
    unit: '克',
    price: '0',
    status: 1,
    categoryId: firstCat ? firstCat.id : null,
    familyId: null
  })
}

/**
 * 新增配菜弹窗：以一份「未保存的草稿」承载表单数据，确认后入列表、
 * 取消即丢；这样旧代码里「在列表里编辑空白行」的不一致就没了。
 */
const addDialog = ref({ visible: false, draft: null })
const openAdd = () => {
  if (addDialog.value.visible) return
  const firstCat = categories.value[0]
  addDialog.value = {
    visible: true,
    draft: {
      name: '',
      emoji: '🥔',
      unit: '克',
      price: '0',
      status: 1,
      categoryId: firstCat ? firstCat.id : null,
      familyId: null
    }
  }
}
const addCatIndex = (id) => {
  const i = categories.value.findIndex(c => c.id === id)
  return i < 0 ? 0 : i
}
const openAddEmoji = () => {
  // 复刻 emojiDialog 的流程，但 target 改成 addDialog.draft
  emojiDialog.value = { visible: true, target: addDialog.value.draft }
}
const confirmAdd = () => {
  const d = addDialog.value.draft
  if (!d) return
  const name = (d.name || '').trim()
  if (!name) {
    uni.showToast({ title: '请填写配菜名', icon: 'none' })
    return
  }
  const p = parseFloat(d.price)
  if (d.price !== '' && d.price !== null && d.price !== undefined && isNaN(p)) {
    uni.showToast({ title: '价格格式错误', icon: 'none' })
    return
  }
  const tmpId = 'tmp-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6)
  list.value.unshift({
    id: null,
    _key: tmpId,
    _isNew: true,
    _deleted: false,
    _formDirty: false,
    name,
    emoji: d.emoji || '🥔',
    unit: d.unit || '',
    price: isNaN(p) ? '0' : String(p),
    status: d.status == null ? 1 : d.status,
    categoryId: d.categoryId || null,
    familyId: null
  })
  addDialog.value = { visible: false, draft: null }
  uni.showToast({ title: '已加入列表，点「保存」提交', icon: 'none' })
}

const cancelNew = (ing) => {
  list.value = list.value.filter(i => i._key !== ing._key)
}

const markDel = (ing) => {
  if (!ing.id) return
  ing._deleted = true
  if (!deletedIds.value.includes(ing.id)) deletedIds.value.push(ing.id)
}

const restoreIng = (ing) => {
  ing._deleted = false
  deletedIds.value = deletedIds.value.filter(x => x !== ing.id)
}

const pickEmoji = (ing) => {
  emojiDialog.value = { visible: true, target: ing }
}
const emojiDialog = ref({ visible: false, target: null })
const emojiOptions = ['🥔','🥬','🍅','🥦','🥕','🧅','🧄','🌽','🍆','🫛','🥚','🧈','🥛','🥩','🍗','🍖','🐟','🦐','🥑','🍋','🌶️','🧂','🍯','🧀','🥥','🥗','🥄','🍳']
const pickEmojiFor = (opt) => {
  if (emojiDialog.value.target) {
    emojiDialog.value.target.emoji = opt
    emojiDialog.value.target._formDirty = true
  }
  emojiDialog.value.visible = false
}

const resetAll = () => {
  if (!hasDirty.value) {
    list.value = []
    deletedIds.value = []
    loadData()
    return
  }
  resetDialog.value.visible = true
}
const confirmReset = () => {
  resetDialog.value.visible = false
  list.value = []
  deletedIds.value = []
  loadData()
}

const saveAll = async () => {
  // 校验
  const toSave = list.value.filter(i => !i._deleted)
  for (const i of toSave) {
    if (!i.name || !i.name.trim()) {
      uni.showToast({ title: '配菜名不能为空', icon: 'none' })
      return
    }
    const p = parseFloat(i.price)
    if (i.price !== '' && i.price !== null && i.price !== undefined && isNaN(p)) {
      uni.showToast({ title: `${i.name} 的价格格式错误`, icon: 'none' })
      return
    }
  }
  saving.value = true
  try {
    const ingredients = toSave
      .map(i => ({
        id: i.id,
        name: i.name,
        emoji: i.emoji,
        unit: i.unit,
        price: parseFloat(i.price) || 0,
        status: i.status,
        categoryId: i.categoryId
      }))
    const res = await batchSaveIngredients({ ingredients, deletedIds: deletedIds.value })
    uni.showToast({ title: `已保存（新增${res.created} 更新${res.updated} 删除${res.deleted}）`, icon: 'success' })
    list.value = []
    deletedIds.value = []
    loadData()
  } catch (e) {
    uni.showToast({ title: '保存失败，请稍后重试', icon: 'none' })
  } finally {
    saving.value = false
  }
}

const goPublicIngredients = () => {
  if (hasDirty.value) {
    uni.showModal({
      title: '有未保存的改动',
      content: '去公共参考配菜后返回将丢失未保存的改动，是否继续？',
      confirmText: '继续前往',
      cancelText: '先保存',
      success: (r) => {
        if (r.confirm) uni.navigateTo({ url: '/pages/public-ingredients/public-ingredients' })
      }
    })
    return
  }
  uni.navigateTo({ url: '/pages/public-ingredients/public-ingredients' })
}

onShow(() => { if (!hasDirty.value) loadData() })
</script>

<style scoped>
/* ============= 甜美少女粉·编辑配菜 ============= */
.edit-ing-page { padding-bottom: 31.25%; }

.header {
  padding: 2% 5% 5%;
  color: var(--c-text);
}
.sub { font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); letter-spacing: 1rpx; }

.error-state { margin: 4%; text-align: center; padding: 12% 6%; }
.error-title { display: block; font-size: var(--t-xl); font-weight: 500; color: var(--c-deep-rose); letter-spacing: 1rpx; }
.error-hint { display: block; font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2); margin-top: 3%; }
.retry-btn { margin-top: 6%; padding: 3% 8%; display: inline-block; font-weight: 400 !important; letter-spacing: 2rpx !important; }

.cat-bar {
  background: rgba(255,255,255,0.5);
  backdrop-filter: blur(16rpx);
  -webkit-backdrop-filter: blur(16rpx);
  border-top: 1rpx solid rgba(255,255,255,0.5);
  border-bottom: 1rpx solid rgba(255,255,255,0.5);
  padding: 2% 0;
}
.cat-scroll { white-space: nowrap; padding: 0 3%; display: flex; gap: 2%; }
.cat-chip {
  display: inline-block; padding: 2% 5%; border-radius: 999rpx;
  background: rgba(255,255,255,0.6); font-size: var(--t-sm); font-weight: 400;
  color: var(--c-deep-rose); flex-shrink: 0;
  border: 1rpx solid rgba(122,74,90,.18);
  white-space: nowrap;
}
.cat-chip.active {
  background: var(--g-primary); color: white; font-weight: 400;
  border-color: transparent;
  box-shadow: var(--glow-pink);
}

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

.ing-list { padding: 3% 4%; display: flex; flex-direction: column; gap: 16rpx; }
.ing-row {
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255,255,255,0.55);
  border-radius: 32rpx; padding: 4% 5%;
  display: flex; align-items: center; gap: 3%;
  box-shadow: var(--glow-soft);
}
.ing-row.removed { opacity: 0.5; background: rgba(255,255,255,0.35); }
.ing-emoji-wrap {
  width: 84rpx; height: 84rpx; border-radius: 50%;
  background: rgba(255,255,255,0.7);
  border: 2rpx solid rgba(255,255,255,0.55);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}

/* emoji 选择器弹窗网格 */
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
.ing-emoji { font-size: var(--t-xxl); }
.ing-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1%; }
.ing-name {
  font-size: var(--t-md); font-weight: 400; color: var(--c-deep-rose);
  padding: 1% 0; background: transparent;
}
.ing-meta {
  display: flex; align-items: center; gap: 1%; flex-wrap: wrap;
  font-size: var(--t-xs); font-weight: 300; color: var(--c-text-2);
}
.meta-input {
  border-bottom: 1rpx solid rgba(122,74,90,.2); padding: 0.5% 1%;
  font-size: var(--t-xs); font-weight: 400; color: var(--c-deep-rose); min-width: 60rpx;
}
.meta-input.unit { width: 80rpx; }
.meta-input.price { width: 100rpx; }
.meta-price { color: var(--c-text-2); font-weight: 300; }
.meta-sep { color: var(--c-text-3); }
.picker {
  font-size: var(--t-xs); font-weight: 400; color: var(--c-primary);
  padding: 0.5% 1%;
  border-bottom: 1rpx solid rgba(122,74,90,.2);
  white-space: nowrap;
}

/* 新增配菜弹窗表单 */
.add-form { width: 100%; display: flex; flex-direction: column; gap: 3%; margin: 4% 0 2%; }
.add-top { display: flex; align-items: center; gap: 3%; }
.add-emoji {
  width: 96rpx; height: 96rpx; border-radius: 50%;
  background: rgba(255,255,255,0.7);
  flex-shrink: 0;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  border: 2rpx dashed rgba(255,123,148,.6);
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}
.add-emoji-text { font-size: 150%; line-height: 1; }
.add-emoji-hint { font-size: var(--t-xs); font-weight: 300; color: var(--c-primary); margin-top: 2rpx; }
.add-name {
  flex: 1; min-width: 0;
  background: rgba(255,255,255,0.7);
  border: 2rpx solid rgba(122,74,90,.2);
  border-radius: 16rpx; padding: 3% 4%;
  font-size: var(--t-md); font-weight: 400; color: var(--c-deep-rose);
}
.add-meta {
  display: flex; align-items: center; gap: 2%; flex-wrap: wrap;
  font-size: var(--t-sm); font-weight: 300; color: var(--c-text-2);
}
.add-meta-input {
  border-bottom: 1rpx solid rgba(122,74,90,.2); padding: 1% 2%;
  font-size: var(--t-sm); font-weight: 400; color: var(--c-deep-rose);
}
.add-meta-input.unit { width: 100rpx; }
.add-meta-input.price { width: 120rpx; }
.add-meta-prefix { color: var(--c-text-2); font-weight: 300; }
.add-meta-sep { color: var(--c-text-3); }
.add-cat-picker {
  font-size: var(--t-sm); font-weight: 400; color: var(--c-primary);
  border-bottom: 1rpx solid rgba(122,74,90,.2); padding: 1% 2%;
  white-space: nowrap;
}

.ing-actions { display: flex; align-items: center; flex-shrink: 0; }
.act-del {
  font-size: var(--t-sm); font-weight: 400; padding: 1.5% 3%; border-radius: 999rpx;
  background: rgba(255,228,226,.85); color: #E5484D;
  white-space: nowrap;
  flex-shrink: 0;
  display: inline-block;
  text-align: center;
}
.act-del.undo { background: rgba(255,255,255,0.7); color: var(--c-primary); border: 1rpx solid rgba(255,123,148,.4); }
.act-del.cancel { background: rgba(255,255,255,0.6); color: var(--c-text-2); }

.add-btn-wrap { padding: 3% 4%; }
.add-btn {
  width: 100%; color: var(--c-primary);
  border: 2rpx dashed rgba(255,123,148,.5); border-radius: 999rpx;
  font-size: var(--t-md); font-weight: 400; padding: 4% 0;
  background: rgba(255,255,255,0.5);
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}
.add-btn.btn-ghost {
  background: rgba(255,255,255,0.5) !important;
  color: var(--c-primary) !important;
  border: 2rpx dashed rgba(255,123,148,.5) !important;
  border-radius: 999rpx !important;
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
  font-size: var(--t-md);
  font-weight: 400;
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
  font-size: var(--t-md);
  font-weight: 400;
  letter-spacing: 2rpx;
  box-shadow: var(--glow-pink);
  margin: 0;
  line-height: 1.4;
}
.save-btn::after { border: none !important; }
.save-btn[disabled] { background: rgba(122,74,90,.45); box-shadow: none; opacity: 0.7; }

/* 加载蒙层 */
.loading-mask {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(248,252,255,0.85);
  display: flex; align-items: center; justify-content: center;
  z-index: 200;
}
.loading-box {
  display: flex; flex-direction: column; align-items: center; gap: 3%;
  background: rgba(255,255,255,0.9);
  backdrop-filter: blur(30rpx);
  -webkit-backdrop-filter: blur(30rpx);
  border: 1rpx solid rgba(255,255,255,0.7);
  padding: 5% 9%; border-radius: 32rpx;
  box-shadow: var(--glow-deep);
}
.loading-text { font-size: 81.25%; font-weight: 300; color: var(--c-text-2); }
.spinner {
  width: 56rpx; height: 56rpx;
  border: 6rpx solid rgba(122,74,90,.2);
  border-top-color: var(--c-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
</style>