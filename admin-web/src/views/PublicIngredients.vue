<template>
  <div class="ingredients-page">
    <div class="page-header">
      <h1>🌐 公共参考配菜</h1>
      <el-button type="primary" size="small" @click="showIngredientDialog()">+ 添加配菜</el-button>
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
              {{ row.status === 1 ? '上架' : '下架' }}
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
              {{ row.status === 1 ? '上架' : '下架' }}
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
        <el-form-item label="状态">
          <el-radio-group v-model="ingForm.status">
            <el-radio :value="1">上架</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
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
  getPublicIngredients, getPublicIngredientCategories,
  createPublicIngredient, updatePublicIngredient,
  deletePublicIngredient as apiDeleteIngredient
} from '../api'

const loading = ref(false)
const isMobile = ref(window.innerWidth < 768)
const handleResize = () => { isMobile.value = window.innerWidth < 768 }
const saving = ref(false)
const categories = ref([])
const ingredients = ref([])
const activeCategory = ref(null)
const ingDialogVisible = ref(false)
const editingIng = ref(null)

const filteredIngredients = computed(() => {
  if (activeCategory.value === null) return ingredients.value
  return ingredients.value.filter(i => i.categoryId === activeCategory.value)
})

const ingForm = ref({ name: '', categoryId: null, unit: '克', price: 0, emoji: '', status: 1 })

const loadAll = async () => {
  loading.value = true
  activeCategory.value = null
  try {
    const [cats, ings] = await Promise.all([
      getPublicIngredientCategories(),
      getPublicIngredients(),
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

const showIngredientDialog = () => {
  editingIng.value = null
  ingForm.value = { name: '', categoryId: categories.value[0]?.id || null, unit: '克', price: 0, emoji: '', status: 1 }
  ingDialogVisible.value = true
}

const editIngredient = (ing) => {
  editingIng.value = ing
  ingForm.value = {
    name: ing.name,
    categoryId: ing.categoryId,
    unit: ing.unit,
    price: parseFloat(ing.price),
    emoji: ing.emoji || '',
    status: ing.status != null ? ing.status : 1
  }
  ingDialogVisible.value = true
}

const saveIngredient = async () => {
  saving.value = true
  try {
    if (editingIng.value) {
      await updatePublicIngredient(editingIng.value.id, ingForm.value)
    } else {
      await createPublicIngredient(ingForm.value)
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
window.addEventListener('resize', handleResize)
onUnmounted(() => {
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
.ic-tag { font-size: 12px; padding: 2px 10px; border-radius: 10px; font-weight: 600; }
.ic-tag.on { background: #e8f5e9; color: #27ae60; }
.ic-tag.off { background: #f5f5f5; color: #999; }
.ic-meta { display: flex; gap: 16px; font-size: 13px; color: #8b6f5c; margin-bottom: 12px; }
.ic-actions { display: flex; gap: 8px; }
.ic-actions .el-button { flex: 1; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h1 { font-size: 22px; color: #3d2314; font-weight: 700; }
.card {
  background: white; border-radius: 16px; padding: 20px; margin-bottom: 16px;
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
.cat-tab.active { background: #FF6B35; color: white; border-color: #FF6B35; }
.cat-tab-name { font-weight: 600; }
</style>
