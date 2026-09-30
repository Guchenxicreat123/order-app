<template>
  <div class="public-menu-page">
    <div class="page-header">
      <h1>🌐 公共参考菜单</h1>
      <el-button type="primary" @click="showDialog('create')">+ 新增菜品</el-button>
    </div>

    <p class="tip">公共菜单所有用户可见，用户可将其加入自己的家庭菜单供参考。</p>

    <el-table v-if="!isMobile" :data="dishes" stripe v-loading="loading" class="mt-4">
      <el-table-column prop="name" label="菜品名称" min-width="120">
        <template #default="{ row }">
          <span class="dish-name-cell">{{ row.imageEmoji }} {{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="categoryId" label="分类" width="100">
        <template #default="{ row }">{{ getCategoryName(row.categoryId) }}</template>
      </el-table-column>
      <el-table-column prop="spiceLevel" label="辣度" width="90">
        <template #default="{ row }">
          <span v-if="row.spiceLevel === 1">🌶️微辣</span>
          <span v-else-if="row.spiceLevel === 2">🌶️🌶️重辣</span>
          <span v-else>🚫不辣</span>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="showDialog('edit', row)">编辑</el-button>
          <el-button size="small" text type="danger" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 移动端卡片视图 -->
    <div v-if="isMobile" class="pm-cards">
      <div v-for="row in dishes" :key="row.id" class="pm-card">
        <div class="pc-head">
          <span class="pc-emoji">{{ row.imageEmoji || '🍱' }}</span>
          <div class="pc-info">
            <div class="pc-name">{{ row.name }}</div>
            <div class="pc-meta">📂 {{ catName(row.categoryId) }} · {{ spiceText(row.spiceLevel) }}</div>
          </div>
          <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '上架' : '下架' }}
          </el-tag>
        </div>
        <div class="pc-desc" v-if="row.description">{{ row.description }}</div>
        <div class="pc-actions">
          <el-button size="small" plain @click="showDialog('edit', row)">编辑</el-button>
          <el-button size="small" type="danger" plain @click="handleDelete(row.id)">删除</el-button>
        </div>
      </div>
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑公共菜品' : '新增公共菜品'" :width="isMobile ? '90%' : '500px'">
      <el-form label-width="70px">
        <el-form-item label="菜名" required>
          <el-input v-model="form.name" placeholder="如：鱼香肉丝" maxlength="64" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.imageEmoji" placeholder="🍳" maxlength="8" style="width:100px" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.categoryId" style="width:100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="辣度">
          <el-radio-group v-model="form.spiceLevel">
            <el-radio :value="0">🚫不辣</el-radio>
            <el-radio :value="1">🌶️微辣</el-radio>
            <el-radio :value="2">🌶️🌶️重辣</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="255" placeholder="简短描述" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">上架</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getPublicDishes, createPublicDish, updatePublicDish, deletePublicDish } from '@/api'
import { getCategories } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const dishes = ref([])
const categories = ref([])
const loading = ref(false)
const isMobile = ref(window.innerWidth < 768)
const handleResize = () => { isMobile.value = window.innerWidth < 768 }
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const form = ref({ name: '', imageEmoji: '', categoryId: null, spiceLevel: 0, description: '', status: 1 })

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})
onUnmounted(() => window.removeEventListener('resize', handleResize))

async function loadData() {
  loading.value = true
  try {
    const [dishData, catData] = await Promise.all([
      getPublicDishes(),
      getCategories()
    ])
    dishes.value = dishData || []
    categories.value = catData || []
  } catch (e) {
    // error handled by api interceptor
  } finally {
    loading.value = false
  }
}

function getCategoryName(id) {
  const c = categories.value.find(x => x.id === id)
  return c ? c.name : '未分类'
}

function showDialog(mode, row) {
  if (mode === 'create') {
    editingId.value = null
    form.value = { name: '', imageEmoji: '', categoryId: null, spiceLevel: 0, description: '', status: 1 }
  } else {
    editingId.value = row.id
    form.value = { ...row }
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.name?.trim()) {
    ElMessage.warning('请输入菜名')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updatePublicDish(editingId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createPublicDish(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    // handled
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('删除后用户将无法再加入此菜品，是否确认删除？', '确认删除', { type: 'warning' })
    await deletePublicDish(id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}
</script>

<style scoped>
.public-menu-page { padding: 20px; }
@media (max-width: 767px) {
  .public-menu-page { padding: 12px; }
  .page-header { flex-wrap: wrap; gap: 8px; }
  .page-header h1 { font-size: 18px; }
}
.pm-cards { display: flex; flex-direction: column; gap: 10px; margin-top: 12px; }
.pm-card { background: white; border-radius: 12px; padding: 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.05); }
.pc-head { display: flex; align-items: center; gap: 12px; }
.pc-emoji { font-size: 32px; }
.pc-info { flex: 1; min-width: 0; }
.pc-name { font-size: 15px; font-weight: 600; color: #3d2314; }
.pc-meta { font-size: 12px; color: #8b6f5c; }
.pc-desc { font-size: 13px; color: #666; margin: 8px 0; }
.pc-actions { display: flex; gap: 8px; }
.pc-actions .el-button { flex: 1; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h1 { margin: 0; font-size: 20px; }
.tip { color: #888; font-size: 13px; margin: 0 0 8px; }
.mt-4 { margin-top: 12px; }
.dish-name-cell { font-size: 15px; }
</style>
