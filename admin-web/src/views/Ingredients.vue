<template>
  <div class="ingredients-page">
    <div class="page-header">
      <h1>🧂 配菜管理</h1>
      <el-button @click="openCategoryManager">编辑分类</el-button>
    </div>

    <!-- 分类筛选 -->
    <div class="section card">
      <div class="section-title">分类筛选</div>
      <div class="category-tabs">
        <div
          class="cat-tab"
          :class="{ active: activeCategory === null }"
          @click="activeCategory = null"
        >
          <span class="cat-tab-name">📋 全部</span>
        </div>
        <div
          v-for="cat in categories"
          :key="cat.id"
          class="cat-tab"
          :class="{ active: activeCategory === cat.id }"
          @click="activeCategory = cat.id"
        >
          <span class="cat-tab-name">{{ cat.emoji }} {{ cat.name }}</span>
        </div>
      </div>
    </div>

    <!-- 配菜列表 -->
    <div class="section card">
      <div class="section-header">
        <div class="section-title">配菜列表 <span class="ing-count">({{ filteredIngredients.length }})</span></div>
        <el-button type="primary" size="small" @click="showIngredientDialog()">+ 添加配菜</el-button>
      </div>

      <el-table v-if="!isMobile" :data="filteredIngredients" stripe style="width:100%" v-loading="loading">
        <el-table-column label="配菜" min-width="160">
          <template #default="{ row }">
            <span>{{ row.emoji }} {{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="categoryId" label="分类" width="120">
          <template #default="{ row }">
            {{ getCategoryName(row.categoryId) }}
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="price" label="单价" width="100">
          <template #default="{ row }">
            ¥{{ row.price }}/{{ row.unit }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停售' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text @click="editIngredient(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="deleteIngredient(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 移动端卡片视图 -->
      <div v-if="isMobile" class="ing-cards">
        <div
          v-for="row in filteredIngredients"
          :key="row.id"
          class="ing-card"
          :class="{ off: row.status === 0 }"
        >
          <div class="ic-row">
            <span class="ic-name">{{ row.emoji }} {{ row.name }}</span>
            <span class="ic-tag" :class="row.status === 1 ? 'on' : 'off'">
              {{ row.status === 1 ? '在用' : '下架' }}
            </span>
          </div>
          <div class="ic-meta">
            <span>📂 {{ getCategoryName(row.categoryId) }}</span>
            <span>¥{{ row.price }}/{{ row.unit }}</span>
          </div>
          <div class="ic-actions">
            <el-button size="small" @click="editIngredient(row)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="deleteIngredient(row)">删除</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 分类管理对话框 -->
    <el-dialog v-model="catMgrVisible" title="编辑分类" :width="isMobile ? '90%' : '480px'">
      <!-- 分类列表（可编辑/删除） -->
      <div class="cat-mgr-list">
        <div v-for="cat in categories" :key="cat.id" class="cat-mgr-row">
          <span class="cmr-emoji">{{ cat.emoji }}</span>
          <span class="cmr-name">{{ cat.name }}</span>
          <span class="cmr-sort">排序 {{ cat.sort }}</span>
          <el-button size="small" text @click="editCategory(cat)">编辑</el-button>
          <el-button size="small" text type="danger" @click="deleteCategory(cat.id)">删除</el-button>
        </div>
        <div v-if="categories.length === 0" class="cat-mgr-empty">暂无分类</div>
      </div>
      <div style="margin-top:12px;">
        <el-button type="primary" plain @click="showCategoryDialog()">+ 新建分类</el-button>
      </div>

      <!-- 新建/编辑分类表单（内嵌） -->
      <el-divider v-if="catFormVisible" />
      <div v-if="catFormVisible" class="cat-form-inline">
        <div class="cfi-title">{{ editingCat ? '编辑分类' : '新建分类' }}</div>
        <el-form label-width="60px">
          <el-form-item label="名称">
            <el-input v-model="catForm.name" placeholder="如：蔬菜" />
          </el-form-item>
          <el-form-item label="图标">
            <el-input v-model="catForm.emoji" placeholder="如 🥬" maxlength="8" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="catForm.sort" :min="0" />
          </el-form-item>
        </el-form>
        <div style="text-align:right;">
          <el-button @click="catFormVisible = false">取消</el-button>
          <el-button type="primary" @click="saveCategory" :loading="saving">保存</el-button>
        </div>
      </div>

      <template #footer>
        <el-button @click="catMgrVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 配菜对话框 -->
    <el-dialog v-model="ingDialogVisible" :title="editingIng ? '编辑配菜' : '添加配菜'" :width="isMobile ? '90%' : '400px'">
      <el-form label-width="70px">
        <el-form-item label="名称">
          <el-input v-model="ingForm.name" placeholder="如：土豆" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="ingForm.categoryId" style="width:100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.emoji + ' ' + c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="ingForm.unit" placeholder="克/个/根/把" />
        </el-form-item>
        <el-form-item label="单价(¥)">
          <el-input-number v-model="ingForm.price" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="ingForm.emoji" placeholder="如 🥔" maxlength="8" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ingDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveIngredient" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getIngredientCategories, createIngredientCategory, updateIngredientCategory, deleteIngredientCategory,
  getIngredientsPublic, createIngredient, updateIngredient,
  deleteIngredient as apiDeleteIngredient
} from '../api'

const loading = ref(false)
const isMobile = ref(window.innerWidth < 768)
const handleResize = () => { isMobile.value = window.innerWidth < 768 }
const saving = ref(false)
const categories = ref([])
const ingredients = ref([])
const activeCategory = ref(null) // null = 全部，否则为选中的分类ID
const catMgrVisible = ref(false)   // 分类管理弹窗
const catFormVisible = ref(false)  // 内嵌的新建/编辑表单
const ingDialogVisible = ref(false)
const editingCat = ref(null)
const editingIng = ref(null)

// 按选中分类筛选配菜
const filteredIngredients = computed(() => {
  if (activeCategory.value === null) return ingredients.value
  return ingredients.value.filter(i => i.categoryId === activeCategory.value)
})

const catForm = ref({ name: '', emoji: '', sort: 0 })
const ingForm = ref({ name: '', categoryId: null, unit: '克', price: 0, emoji: '' })

const loadAll = async () => {
  loading.value = true
  activeCategory.value = null
  try {
    const [cats, ings] = await Promise.all([
      getIngredientCategories(),
      getIngredientsPublic(),
    ])
    categories.value = cats || []
    ingredients.value = ings || []
  } finally {
    loading.value = false
  }
}

const getCategoryName = (id) => {
  const c = categories.value.find(x => x.id === id)
  return c ? `${c.emoji} ${c.name}` : '-'
}

// 分类管理
const openCategoryManager = () => {
  catFormVisible.value = false
  catMgrVisible.value = true
}

const showCategoryDialog = () => {
  editingCat.value = null
  catForm.value = { name: '', emoji: '', sort: 0 }
  catFormVisible.value = true
}

const editCategory = (cat) => {
  editingCat.value = cat
  catForm.value = { name: cat.name, emoji: cat.emoji || '', sort: cat.sort || 0 }
  catFormVisible.value = true
}

const saveCategory = async () => {
  saving.value = true
  try {
    if (editingCat.value) {
      await updateIngredientCategory(editingCat.value.id, catForm.value)
      ElMessage.success('更新成功')
    } else {
      await createIngredientCategory(catForm.value)
      ElMessage.success('创建成功')
    }
    catFormVisible.value = false
    editingCat.value = null
    loadAll()
  } finally {
    saving.value = false
  }
}

const deleteCategory = async (id) => {
  await ElMessageBox.confirm('删除分类后，该分类下的配菜将失去分类。确定删除？', '删除分类', { type: 'warning' })
  await deleteIngredientCategory(id)
  ElMessage.success('已删除')
  // 如果删的是当前筛选的分类，重置为全部
  if (activeCategory.value === id) activeCategory.value = null
  loadAll()
}

// 配菜
const showIngredientDialog = () => {
  editingIng.value = null
  ingForm.value = { name: '', categoryId: categories.value[0]?.id || null, unit: '克', price: 0, emoji: '' }
  ingDialogVisible.value = true
}

const editIngredient = (ing) => {
  editingIng.value = ing
  ingForm.value = { name: ing.name, categoryId: ing.categoryId, unit: ing.unit, price: parseFloat(ing.price), emoji: ing.emoji || '' }
  ingDialogVisible.value = true
}

const saveIngredient = async () => {
  saving.value = true
  try {
    const payload = { ...ingForm.value, status: 1 }
    if (editingIng.value) {
      await updateIngredient(editingIng.value.id, payload)
    } else {
      await createIngredient(payload)
    }
    ElMessage.success('保存成功')
    ingDialogVisible.value = false
    loadAll()
  } finally {
    saving.value = false
  }
}

const deleteIngredient = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除配菜「${row.name}」？`, '删除确认', { type: 'warning' })
  } catch (e) { return }
  await apiDeleteIngredient(row.id)
  ElMessage.success('已删除')
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
.ingredients-page { padding: 24px; max-width: 900px; margin: 0 auto; }
@media (max-width: 767px) {
  .ingredients-page { padding: 12px; }
  .page-header { flex-wrap: wrap; gap: 8px; }
  .page-header h1 { font-size: 18px; }
}
.ing-cards { display: flex; flex-direction: column; gap: 12px; }
.ing-card {
  background: white; border-radius: 12px; padding: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}
.ing-card.off { opacity: 0.6; }
.ic-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.ic-name { font-size: 16px; font-weight: 700; color: #3d2314; }
.ic-tag {
  font-size: 12px; padding: 2px 10px; border-radius: 10px; font-weight: 600;
}
.ic-tag.on { background: #e8f5e9; color: #27ae60; }
.ic-tag.off { background: #f5f5f5; color: #999; }
.ic-meta { display: flex; gap: 16px; font-size: 13px; color: #8b6f5c; margin-bottom: 12px; }
.ic-actions { display: flex; gap: 8px; }
.ic-actions .el-button { flex: 1; }

.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h1 { font-size: 22px; color: #3d2314; font-weight: 700; }

.card {
  background: white;
  border-radius: 16px;
  padding: 20px;
  margin-bottom: 16px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
}

.section-title { font-size: 15px; font-weight: 600; color: #3d2314; margin-bottom: 14px; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.ing-count { font-size: 13px; color: #c4a882; font-weight: 400; }

.category-tabs { display: flex; flex-wrap: wrap; gap: 10px; }
.cat-tab {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 14px; background: #faf8f5; border-radius: 10px;
  font-size: 14px; cursor: pointer; border: 2px solid transparent;
  transition: all 0.15s; user-select: none;
}
.cat-tab:hover { background: #fff3e0; }
.cat-tab.active {
  background: #FF6B35; color: white; border-color: #FF6B35;
}
.cat-tab-name { font-weight: 600; }

/* 分类管理弹窗 */
.cat-mgr-list { display: flex; flex-direction: column; gap: 8px; }
.cat-mgr-row {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px; background: #faf8f5; border-radius: 10px; font-size: 14px;
}
.cmr-emoji { font-size: 18px; }
.cmr-name { font-weight: 600; color: #3d2314; flex: 1; }
.cmr-sort { font-size: 12px; color: #c4a882; }
.cat-mgr-empty { text-align: center; color: #c4a882; padding: 20px 0; }
.cat-form-inline { margin-top: 8px; }
.cfi-title { font-size: 15px; font-weight: 600; color: #3d2314; margin-bottom: 12px; }
</style>
