<template>
  <div class="dishes-page">
    <div class="page-header">
      <h1>🍳 菜谱管理</h1>
      <el-button type="primary" @click="showDishDialog()">+ 新建菜谱</el-button>
    </div>

    <!-- 菜谱卡片列表 -->
    <div class="dish-grid">
      <div v-for="dish in dishes" :key="dish.id" class="dish-card card">
        <div class="dish-emoji">{{ dish.imageEmoji || '🍽️' }}</div>
        <div class="dish-info">
          <div class="dish-name">{{ dish.name }}</div>
          <div class="dish-meta">
            <el-tag size="small" :type="dish.status === 1 ? 'success' : 'info'">
              {{ dish.status === 1 ? '上架' : '下架' }}
            </el-tag>
            <span class="dish-price">¥{{ dish.price }}</span>
          </div>
          <div class="dish-spice">
            <span v-if="dish.spiceLevel === 1">🌶️微辣</span>
            <span v-else-if="dish.spiceLevel === 2">🌶️重辣</span>
            <span v-else>🚫不辣</span>
          </div>
        </div>
        <div class="dish-actions">
          <el-button size="small" text @click="editDish(dish)">编辑</el-button>
          <el-button size="small" text :type="dish.status === 1 ? 'danger' : 'success'" @click="toggleDish(dish.id)">
            {{ dish.status === 1 ? '下架' : '上架' }}
          </el-button>
        </div>
      </div>
      <div v-if="dishes.length === 0" class="empty">暂无菜谱，点击上方按钮创建</div>
    </div>

    <!-- 菜谱编辑对话框 -->
    <el-dialog v-model="dishDialogVisible" :title="editingDish ? '编辑菜谱' : '新建菜谱'" :width="isMobile ? '95%' : '820px'" :fullscreen="isMobile" top="2vh">
      <div class="dish-editor">
        <!-- 基本信息 -->
        <div class="editor-basic">
          <el-form label-width="70px">
            <el-form-item label="菜名">
              <el-input v-model="dishForm.name" placeholder="如：番茄炒蛋" />
            </el-form-item>
            <el-form-item label="图标">
              <el-input v-model="dishForm.imageEmoji" placeholder="🍳" maxlength="8" style="width:100px" />
            </el-form-item>
            <el-form-item label="分类">
              <el-select v-model="dishForm.categoryId" style="width:100%">
                <el-option v-for="c in dishCategories" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="辣度">
              <el-radio-group v-model="dishForm.spiceLevel">
                <el-radio :value="0">🚫 免辣</el-radio>
                <el-radio :value="1">🌶️ 微辣</el-radio>
                <el-radio :value="2">🌶️ 重辣</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-form>
        </div>

        <!-- 双列编辑器 -->
        <div class="dual-editor">
          <!-- 左列：配菜库 -->
          <div class="editor-left">
            <div class="editor-left-header">
              <span>🗃️ 配菜库</span>
              <el-input v-model="ingSearch" size="small" placeholder="搜索配菜" clearable style="width:120px" />
            </div>
            <div class="ing-categories">
              <el-tag
                v-for="cat in ingCategories"
                :key="cat.id"
                :type="activeCategory === cat.id ? 'primary' : 'info'"
                size="small"
                class="cat-tag"
                @click="activeCategory = activeCategory === cat.id ? null : cat.id"
                style="cursor:pointer"
              >{{ cat.emoji || '' }} {{ cat.name }}</el-tag>
            </div>
            <div class="ing-list">
              <div
                v-for="ing in filteredIngredients"
                :key="ing.id"
                class="ing-item"
                draggable="true"
                @dragstart="onDragStart($event, ing, 'left')"
              >
                <span class="ing-emoji">{{ ing.emoji }}</span>
                <span class="ing-name">{{ ing.name }}</span>
                <span class="ing-price">¥{{ ing.price }}/{{ ing.unit }}</span>
              </div>
            </div>
          </div>

          <!-- 拖拽提示 -->
          <div class="drag-hint">← 拖拽或点击添加 →</div>

          <!-- 右列：已选配菜 -->
          <div
            class="editor-right"
            @dragover.prevent="onDragOver"
            @drop="onDrop"
            :class="{ 'drag-over': isDragOver }"
          >
            <div class="editor-right-header">
              <span>🍽️ 菜谱配菜</span>
              <span class="selected-count">{{ selectedIngredients.length }} 种</span>
            </div>

            <div v-if="selectedIngredients.length === 0" class="editor-empty">
              从左侧拖入配菜，或点击添加
            </div>

            <div v-else class="selected-list">
              <div
                v-for="sel in selectedIngredients"
                :key="sel.id"
                class="selected-item"
                @click="addOrRemove(sel)"
              >
                <div class="sel-info">
                  <span class="sel-emoji">{{ sel.emoji }}</span>
                  <span class="sel-name">{{ sel.name }}</span>
                </div>
                <div class="sel-right">
                  <el-input-number
                    v-model="sel.amount"
                    :min="0"
                    :step="10"
                    size="small"
                    controls-position="right"
                    style="width:110px"
                    @click.stop
                  />
                  <span class="sel-unit">{{ sel.unit }}</span>
                  <span class="sel-price">¥{{ sel.price }}</span>
                  <span class="sel-remove" @click.stop="removeIngredient(sel.id)">✕</span>
                </div>
              </div>
            </div>

            <div class="editor-total">
              <span>预估价格：</span>
              <strong>¥{{ estimatedPrice }}</strong>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="dishDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDish" :loading="saving">
          保存菜谱
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getDishesManageAll, getDishDetail, createDish, updateDish, toggleDishStatus,
  getIngredientsPublic, getIngredientCategories, getCategories
} from '../api'

const loading = ref(false)
const saving = ref(false)
const isMobile = ref(window.innerWidth < 768)
const handleResize = () => { isMobile.value = window.innerWidth < 768 }
const dishes = ref([])
const dishCategories = ref([])    // 菜品分类（热菜/凉菜/…）
const ingCategories = ref([])    // 配菜分类（蔬菜/蛋奶/…）
const allIngredients = ref([])
const dishDialogVisible = ref(false)
const editingDish = ref(null)
const dishForm = ref({ name: '', imageEmoji: '', categoryId: null, spiceLevel: 0 })
const selectedIngredients = ref([])
const ingSearch = ref('')
const activeCategory = ref(null)
const isDragOver = ref(false)

const filteredIngredients = computed(() => {
  let list = allIngredients.value
  if (activeCategory.value) list = list.filter(i => i.categoryId === activeCategory.value)
  if (ingSearch.value) list = list.filter(i => i.name.includes(ingSearch.value))
  return list
})

const estimatedPrice = computed(() => {
  const total = selectedIngredients.value.reduce((sum, ing) => {
    return sum + (parseFloat(ing.price) * (parseFloat(ing.amount) || 0))
  }, 0)
  return total.toFixed(2)
})

const loadAll = async () => {
  loading.value = true
  try {
    const [d, dishCats, ingCats, ings] = await Promise.all([
      getDishesManageAll(),
      getCategories(),
      getIngredientCategories(),
      getIngredientsPublic(),
    ])
    dishes.value = d || []
    dishCategories.value = dishCats || []
    ingCategories.value = ingCats || []
    allIngredients.value = ings || []
    if (dishCats.length > 0 && !dishForm.value.categoryId) {
      dishForm.value.categoryId = dishCats[0].id
    }
  } finally {
    loading.value = false
  }
}

const showDishDialog = () => {
  editingDish.value = null
  dishForm.value = { name: '', imageEmoji: '', categoryId: dishCategories.value[0]?.id || null, spiceLevel: 0 }
  selectedIngredients.value = []
  dishDialogVisible.value = true
}

const editDish = async (dish) => {
  editingDish.value = dish
  dishForm.value = {
    name: dish.name,
    imageEmoji: dish.imageEmoji || '',
    categoryId: dish.categoryId,
    spiceLevel: dish.spiceLevel || 0,
  }
  // 加载已选配菜
  try {
    const detail = await getDishDetail(dish.id)
    selectedIngredients.value = (detail.ingredients || []).map(i => ({
      id: i.ingId,
      name: i.name,
      emoji: i.emoji || '',
      unit: i.unit,
      price: parseFloat(i.price),
      amount: parseFloat(i.amount),
    }))
  } catch (e) {
    selectedIngredients.value = []
  }
  dishDialogVisible.value = true
}

const addOrRemove = (ing) => {
  const idx = selectedIngredients.value.findIndex(s => s.id === ing.id)
  if (idx >= 0) {
    selectedIngredients.value.splice(idx, 1)
  } else {
    selectedIngredients.value.push({ ...ing, amount: 100 })
  }
}

const removeIngredient = (id) => {
  selectedIngredients.value = selectedIngredients.value.filter(s => s.id !== id)
}

const onDragStart = (e, ing, from) => {
  e.dataTransfer.setData('ing', JSON.stringify(ing))
  e.dataTransfer.setData('from', from)
}

const onDragOver = () => { isDragOver.value = true }

const onDrop = (e) => {
  isDragOver.value = false
  const ing = JSON.parse(e.dataTransfer.getData('ing') || '{}')
  if (!ing.id) return
  if (!selectedIngredients.value.find(s => s.id === ing.id)) {
    selectedIngredients.value.push({ ...ing, amount: 100 })
  }
}

const saveDish = async () => {
  if (!dishForm.value.name) {
    ElMessage.warning('请输入菜名')
    return
  }
  saving.value = true
  try {
    const payload = {
      ...dishForm.value,
      ingredients: selectedIngredients.value.map(s => ({
        ingId: s.id,
        amount: parseFloat(s.amount) || 100,
        unit: s.unit,
      })),
    }
    if (editingDish.value) {
      await updateDish(editingDish.value.id, payload)
    } else {
      await createDish(payload)
    }
    ElMessage.success('保存成功')
    dishDialogVisible.value = false
    loadAll()
  } finally {
    saving.value = false
  }
}

const toggleDish = async (id) => {
  await toggleDishStatus(id)
  ElMessage.success('已切换状态')
  loadAll()
}

onMounted(loadAll)
// 监听家庭切换事件，切换后自动刷新
window.addEventListener('admin-family-changed', loadAll)
window.addEventListener('family-changed', loadAll)
window.addEventListener('resize', handleResize)
onUnmounted(() => {
  window.removeEventListener('admin-family-changed', loadAll)
  window.removeEventListener('family-changed', loadAll)
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.dishes-page { padding: 24px; }
@media (max-width: 767px) {
  .dishes-page { padding: 12px; }
  .page-header { flex-wrap: wrap; gap: 8px; }
  .page-header h1 { font-size: 18px; }
  .page-header .header-actions { width: 100%; flex-wrap: wrap; }
  .dish-grid { grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 10px; }
}

.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h1 { font-size: 22px; color: #3d2314; font-weight: 700; }

.dish-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 16px; }

.card { background: white; border-radius: 16px; padding: 20px; box-shadow: 0 2px 12px rgba(0,0,0,0.06); }

.dish-card { display: flex; flex-direction: column; align-items: center; gap: 10px; }
.dish-emoji { font-size: 42px; }
.dish-info { text-align: center; width: 100%; }
.dish-name { font-size: 15px; font-weight: 600; color: #3d2314; margin-bottom: 6px; }
.dish-meta { display: flex; justify-content: center; align-items: center; gap: 8px; margin-bottom: 4px; }
.dish-price { font-size: 14px; color: #FF6B35; font-weight: 600; }
.dish-spice { font-size: 12px; color: #8b6f5c; }
.dish-actions { display: flex; gap: 4px; width: 100%; justify-content: center; }

.empty { grid-column: 1 / -1; text-align: center; color: #8b6f5c; padding: 32px; }

.dish-editor { display: flex; flex-direction: column; gap: 16px; }
.editor-basic { border-bottom: 1px solid #f0e6dc; padding-bottom: 16px; }

.dual-editor { display: flex; gap: 16px; align-items: stretch; min-height: 340px; }

.editor-left {
  width: 280px;
  border: 1px solid #f0e6dc;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.editor-left-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: #faf8f5;
  border-bottom: 1px solid #f0e6dc;
  font-size: 13px;
  font-weight: 600;
  color: #3d2314;
}

.ing-categories { padding: 8px 10px; display: flex; flex-wrap: wrap; gap: 4px; border-bottom: 1px solid #f0e6dc; }
.cat-tag { cursor: pointer; }

.ing-list { flex: 1; overflow-y: auto; padding: 8px; }

.ing-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: grab;
  transition: background 0.15s;
  margin-bottom: 4px;
}

.ing-item:hover { background: #fff3e0; }
.ing-item:active { cursor: grabbing; }

.ing-emoji { font-size: 18px; }
.ing-name { flex: 1; font-size: 13px; color: #3d2314; }
.ing-price { font-size: 11px; color: #FF6B35; }

.drag-hint {
  display: flex;
  align-items: center;
  font-size: 12px;
  color: #c4a882;
  writing-mode: vertical-rl;
  min-width: 30px;
}

.editor-right {
  flex: 1;
  border: 2px dashed #e8d5c4;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  transition: border-color 0.2s, background 0.2s;
  overflow: hidden;
}

.editor-right.drag-over {
  border-color: #FF6B35;
  background: #fff8f5;
}

.editor-right-header {
  display: flex;
  justify-content: space-between;
  padding: 10px 14px;
  background: #faf8f5;
  border-bottom: 1px solid #f0e6dc;
  font-size: 13px;
  font-weight: 600;
  color: #3d2314;
}

.selected-count { color: #FF6B35; font-weight: 600; }

.editor-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c4a882;
  font-size: 13px;
}

.selected-list { flex: 1; overflow-y: auto; padding: 10px; display: flex; flex-direction: column; gap: 8px; }

.selected-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: white;
  border: 1px solid #f0e6dc;
  border-radius: 10px;
  gap: 8px;
}

.sel-info { display: flex; align-items: center; gap: 6px; min-width: 0; }
.sel-emoji { font-size: 18px; }
.sel-name { font-size: 13px; color: #3d2314; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.sel-right { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.sel-unit { font-size: 12px; color: #8b6f5c; min-width: 20px; }
.sel-price { font-size: 12px; color: #FF6B35; font-weight: 600; min-width: 40px; text-align: right; }
.sel-remove { color: #ccc; cursor: pointer; font-size: 14px; padding: 2px 6px; }
.sel-remove:hover { color: #ff4d4f; }

.editor-total {
  padding: 10px 14px;
  border-top: 1px solid #f0e6dc;
  text-align: right;
  font-size: 13px;
  color: #8b6f5c;
  background: #faf8f5;
}

.editor-total strong { color: #FF6B35; font-size: 16px; margin-left: 6px; }
</style>
