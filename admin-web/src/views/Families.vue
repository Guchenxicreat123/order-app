<template>
  <div class="families-page" :class="{ mobile: isMobile }">
    <!-- 家庭一览列表 -->
    <template v-if="!currentFamily">
      <div class="page-header">
        <h1>🏠 家庭一览</h1>
        <div class="header-actions">
          <el-input
            v-model="keyword"
            placeholder="搜索家庭名 / 代码 / 成员"
            clearable
            style="width:240px"
            @input="onSearch"
          >
            <template #prefix>🔍</template>
          </el-input>
          <el-button type="primary" @click="openCreate">＋ 新建家庭</el-button>
          <el-button type="success" @click="loadData" :loading="loading">刷新</el-button>
        </div>
      </div>

      <el-empty v-if="!loading && filteredFamilies.length === 0" description="暂无家庭" />

      <!-- 桌面端表格 -->
      <el-table
        v-if="!isMobile"
        :data="filteredFamilies"
        :loading="loading"
        row-key="familyId"
        style="width:100%"
        @row-click="enterFamily"
      >
        <el-table-column label="ID" prop="familyId" width="70" />
        <el-table-column label="家庭名称" prop="name" min-width="150">
          <template #default="{ row }">
            <span class="fam-name-link">🏠 {{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="加入码" prop="code" min-width="110">
          <template #default="{ row }">
            <code class="fam-code">{{ row.code }}</code>
          </template>
        </el-table-column>
        <el-table-column label="创建者" min-width="100">
          <template #default="{ row }">
            <span v-if="row.ownerName">{{ row.ownerName }}</span>
            <span v-else style="color:#c4a882">-</span>
          </template>
        </el-table-column>
        <el-table-column label="主厨" min-width="100">
          <template #default="{ row }">
            <el-tag v-if="row.chefName" type="warning" size="small" effect="plain">{{ row.chefName }}</el-tag>
            <span v-else style="color:#c4a882">-</span>
          </template>
        </el-table-column>
        <el-table-column label="成员数" prop="memberCount" width="90" />
        <el-table-column label="创建时间" min-width="120">
          <template #default="{ row }">
            {{ fmtDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click.stop="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" plain @click.stop="openDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <el-table-column label="" width="50">
          <template #default>
            <span style="color:#c4a882;font-size:16px">›</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 移动端卡片列表 -->
      <div v-if="isMobile" class="fam-cards" :class="{ empty: filteredFamilies.length === 0 }">
        <div
          v-for="row in filteredFamilies"
          :key="row.familyId"
          class="fam-card"
          @click="enterFamily(row)"
        >
          <div class="fc-row">
            <span class="fc-name">🏠 {{ row.name }}</span>
            <code class="fc-code">{{ row.code }}</code>
          </div>
          <div class="fc-meta">
            <span class="fc-meta-item">
              👑 {{ row.ownerName || '-' }}
            </span>
            <span class="fc-meta-item" v-if="row.chefName">
              👨‍🍳 {{ row.chefName }}
            </span>
            <span class="fc-meta-item">
              👥 {{ row.memberCount }} 人
            </span>
          </div>
          <div class="fc-actions">
            <el-button size="small" type="primary" plain @click.stop="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" plain @click.stop="openDelete(row)">删除</el-button>
          </div>
        </div>
      </div>

      <!-- 未分组用户（没加入任何家庭的用户） -->
      <div v-if="orphanUsers.length > 0" class="orphan-section">
        <div class="orphan-title">
          👥 未分组用户
          <span class="orphan-count">({{ orphanUsers.length }})</span>
        </div>
        <div class="orphan-list">
          <div v-for="u in orphanUsers" :key="u.userId" class="orphan-item">
            <span class="oi-emoji">👤</span>
            <span class="oi-name">{{ u.nickname || '(未命名)' }}</span>
            <span class="oi-id">#{{ u.userId }}</span>
            <el-button size="small" type="primary" plain @click="openAssignOrphan(u)">加入家庭</el-button>
            <el-button size="small" type="warning" plain @click="revokeUser(u)">踢下线</el-button>
            <el-button size="small" type="danger" plain @click="deleteOrphanUser(u)">删除</el-button>
          </div>
        </div>
      </div>
    </template>

    <!-- 点击家庭后的用户一览 -->
    <template v-else>
      <div class="crumb">
        <a class="crumb-back" @click="backToList">🏠 家庭一览</a>
        <span class="crumb-sep">/</span>
        <span class="crumb-now">{{ currentFamily.name }}</span>
      </div>
      <FamilyUsers
        :key="currentFamily.familyId"
        :family="currentFamily"
        @changed="reloadCurrent"
      />
    </template>

    <!-- 新建 / 编辑家庭 -->
    <el-dialog v-model="formVisible" :title="editingFamily ? '编辑家庭' : '新建家庭'" :width="isMobile ? '90%' : '440px'">
      <el-form label-width="90px">
        <el-form-item label="家庭名称">
          <el-input v-model="formName" placeholder="如：张阿姨家" maxlength="32" show-word-limit />
        </el-form-item>
        <el-form-item v-if="editingFamily" label="加入码">
          <div style="display:flex;gap:8px;width:100%">
            <el-input
              v-model="formCode"
              placeholder="4~8 位字母或数字"
              maxlength="8"
              style="flex:1"
              @input="onCodeInput"
            />
            <el-button @click="regenCode" title="随机生成一个未被占用的加入码">随机</el-button>
          </div>
          <div style="color:#c4a882;font-size:12px;margin-top:4px">
            家人凭这个码加入家庭，全局唯一。改码后<strong>旧码立即失效</strong>。
          </div>
        </el-form-item>
        <el-form-item v-if="!editingFamily" label="创建者">
          <el-select
            v-model="formOwnerId"
            filterable
            placeholder="选谁成为创建者（默认本人）"
            clearable
            style="width:100%"
          >
            <el-option
              v-for="u in allUsers"
              :key="u.userId"
              :label="`${u.nickname || '(未命名)'} (#${u.userId})`"
              :value="u.userId"
            />
          </el-select>
          <div style="color:#c4a882;font-size:12px;margin-top:4px">留空则默认使用您（admin）</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">{{ editingFamily ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>

    <!-- 删除确认 -->
    <el-dialog v-model="deleteVisible" title="删除家庭" :width="isMobile ? '90%' : '460px'">
      <div style="padding:8px 0;color:#3d2314;">
        <p style="margin:0 0 12px;">确定要删除家庭 <strong>{{ deletingFamily && deletingFamily.name }}</strong> 吗？</p>
        <p style="margin:0;color:#c0392b;font-size:13px;">
          ⚠️ 此操作不可恢复，将同时删除该家庭的所有成员关系、菜品、菜单、购物车、配料等全部数据。
        </p>
      </div>
      <template #footer>
        <el-button @click="deleteVisible = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="submitDelete">确认删除</el-button>
      </template>
    </el-dialog>

    <!-- 未分组用户加入家庭 -->
    <el-dialog v-model="assignOrphanVisible" title="加入家庭" :width="isMobile ? '90%' : '400px'">
      <div style="padding:8px 0;color:#3d2314;">
        <p style="margin:0 0 16px;">
          👤 {{ assigningUser?.nickname || '(未命名)' }} (#{{ assigningUser?.userId }})
        </p>
        <el-select v-model="assignFamilyId" filterable placeholder="选择要加入的家庭" style="width:100%">
          <el-option
            v-for="f in families"
            :key="f.familyId"
            :label="f.name + '（' + (f.memberCount || 0) + '人）'"
            :value="f.familyId"
          />
        </el-select>
      </div>
      <template #footer>
        <el-button @click="assignOrphanVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAssignOrphan">确认加入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminFamiliesOverview, getAdminUsers, createFamily, updateFamily, deleteFamily, adminAddFamily, adminDeleteUser, adminRevokeUser } from '../api'
import FamilyUsers from './FamilyUsers.vue'

const loading = ref(false)
const isMobile = ref(window.innerWidth < 768)
const saving = ref(false)
const families = ref([])
const allUsers = ref([])
const keyword = ref('')
const currentFamily = ref(null)

// form
const formVisible = ref(false)
const editingFamily = ref(null)   // null = create, object = edit
const formName = ref('')
const formCode = ref('')
const formOwnerId = ref(null)

// delete
const deleteVisible = ref(false)
const deletingFamily = ref(null)

const filteredFamilies = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return families.value
  return families.value.filter(f =>
    (f.name || '').toLowerCase().includes(kw) ||
    (f.code || '').toLowerCase().includes(kw) ||
    (f.members || []).some(m => (m.nickname || '').toLowerCase().includes(kw))
  )
})

function fmtDate(s) {
  if (!s) return '-'
  const d = new Date(s)
  if (isNaN(d)) return '-'
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const loadData = async () => {
  loading.value = true
  try {
    const [fams, users] = await Promise.all([
      getAdminFamiliesOverview(),
      getAdminUsers().catch(() => [])
    ])
    families.value = fams || []
    allUsers.value = users || []
  } catch (e) {
    families.value = []
    allUsers.value = []
  } finally {
    loading.value = false
  }
}

// 未加入任何家庭的用户（孤立用户）
const orphanUsers = computed(() => {
  // 收集所有家庭中已出现的 userId
  const inFamily = new Set()
  for (const f of families.value) {
    for (const m of (f.members || [])) {
      inFamily.add(m.userId)
    }
  }
  // allUsers 中不在任何家庭的
  return allUsers.value.filter(u => !inFamily.has(u.userId))
})

const loadAllUsers = async () => {
  if (allUsers.value.length > 0) return
  try { allUsers.value = await getAdminUsers() || [] } catch { allUsers.value = [] }
}

// 把孤立用户加入某家庭
const assignOrphanVisible = ref(false)
const assigningUser = ref(null)
const assignFamilyId = ref(null)

function openAssignOrphan(u) {
  assigningUser.value = u
  assignFamilyId.value = null
  assignOrphanVisible.value = true
}

async function submitAssignOrphan() {
  if (!assignFamilyId.value) { ElMessage.warning('请选择家庭'); return }
  saving.value = true
  try {
    await adminAddFamily(assigningUser.value.userId, { familyId: assignFamilyId.value })
    ElMessage.success('已加入家庭')
    assignOrphanVisible.value = false
    await loadData()
  } catch (e) {} finally {
    saving.value = false
  }
}

// 删除未分组用户
async function deleteOrphanUser(u) {
  try {
    await ElMessageBox.confirm(
      `确定删除用户「${u.nickname || '(未命名)'}」(#${u.userId})？\n` +
      `该用户未加入任何家庭。删除后不可恢复，会连带删除它的账号、历史订单、推送记录、购物车。`,
      '删除用户',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch (e) { return }
  saving.value = true
  try {
    await adminDeleteUser(u.userId)
    ElMessage.success('用户已删除')
    await loadData()
  } catch (e) {} finally {
    saving.value = false
  }
}

/** 撤销指定用户全部 token（强制下线）。用户需重新登录才能继续访问。 */
async function revokeUser(u) {
  try {
    await ElMessageBox.confirm(
      `确认踢下线「${u.nickname || '(未命名)'}」(#${u.userId})？\n该用户当前所有登录设备将立即失效，需重新登录。`,
      '踢下线',
      { type: 'warning', confirmButtonText: '确认踢下线', cancelButtonText: '取消' }
    )
  } catch (e) { return }
  saving.value = true
  try {
    await adminRevokeUser(u.userId)
    ElMessage.success('已踢下线，该用户需重新登录')
  } catch (e) {} finally {
    saving.value = false
  }
}

function onSearch() {}

// ---- CRUD ----

function openCreate() {
  editingFamily.value = null
  formName.value = ''
  formCode.value = ''
  formOwnerId.value = null
  formVisible.value = true
  loadAllUsers()
}

function openEdit(row) {
  editingFamily.value = row
  formName.value = row.name
  formCode.value = row.code || ''
  formOwnerId.value = null
  formVisible.value = true
}

const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
function genRandomCode(len = 6) {
  let s = ''
  for (let i = 0; i < len; i++) s += CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)]
  return s
}
function regenCode() {
  // 简单随机：后端会再校验全局唯一并报错提示
  formCode.value = genRandomCode(6)
}
// 输入时统一大写、去非法字符，校验 4~8 位
function onCodeInput(v) {
  formCode.value = String(v || '').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8)
}

async function submitForm() {
  if (!formName.value.trim()) { ElMessage.warning('请输入家庭名称'); return }
  if (editingFamily.value) {
    const code = formCode.value.trim()
    if (code && (code.length < 4 || code.length > 8)) {
      ElMessage.warning('加入码需为 4~8 位字母或数字')
      return
    }
  }
  saving.value = true
  try {
    if (editingFamily.value) {
      const payload = { name: formName.value.trim() }
      if (formCode.value.trim()) payload.code = formCode.value.trim()
      await updateFamily(editingFamily.value.familyId, payload)
      ElMessage.success('已保存')
    } else {
      const payload = { name: formName.value.trim() }
      if (formOwnerId.value) payload.ownerUserId = formOwnerId.value
      await createFamily(payload)
      ElMessage.success('家庭已创建')
    }
    formVisible.value = false
    await loadData()
    window.dispatchEvent(new CustomEvent('families-changed'))
  } catch (e) {} finally { saving.value = false }
}

function openDelete(row) {
  deletingFamily.value = row
  deleteVisible.value = true
}

async function submitDelete() {
  saving.value = true
  try {
    await deleteFamily(deletingFamily.value.familyId)
    ElMessage.success('家庭已删除')
    deleteVisible.value = false
    // 如果正在该家庭详情页，自动退回列表
    if (currentFamily.value && currentFamily.value.familyId === deletingFamily.value.familyId) {
      currentFamily.value = null
    }
    await loadData()
    window.dispatchEvent(new CustomEvent('families-changed'))
  } catch (e) {} finally { saving.value = false }
}

// ---- 导航 ----

function enterFamily(row) {
  currentFamily.value = row
}

function backToList() {
  currentFamily.value = null
}

const reloadCurrent = async () => {
  const fid = currentFamily.value && currentFamily.value.familyId
  await loadData()
  if (fid) {
    currentFamily.value = families.value.find(f => f.familyId === fid) || null
  }
  window.dispatchEvent(new CustomEvent('families-changed'))
}

onMounted(() => {
  loadData()
  window.addEventListener('admin-family-changed', loadData)
  window.addEventListener('resize', () => { isMobile.value = window.innerWidth < 768 })
})
onUnmounted(() => {
  window.removeEventListener('resize', () => {})
})
</script>

<style scoped>
.families-page { padding: 24px; max-width: 1100px; margin: 0 auto; }
.families-page.mobile { padding: 12px; max-width: 100%; }
.families-page.mobile .page-header { flex-wrap: wrap; gap: 8px; }
.families-page.mobile .page-header h1 { font-size: 18px; }
.families-page.mobile .header-actions { width: 100%; }
.families-page.mobile .header-actions .el-input { flex: 1; }

/* 移动端卡片视图 */
.fam-cards { display: flex; flex-direction: column; gap: 12px; }
.fam-card {
  background: white;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  cursor: pointer;
  transition: all 0.2s;
}
.fam-card:active { transform: scale(0.99); background: #faf8f5; }
.fc-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.fc-name { font-size: 16px; font-weight: 700; color: #3d2314; }
.fc-code {
  background: #fff3e0; color: #e67e22;
  padding: 2px 10px; border-radius: 8px;
  font-size: 14px; font-family: monospace;
}
.fc-meta { display: flex; gap: 12px; font-size: 13px; color: #8b6f5c; margin-bottom: 12px; flex-wrap: wrap; }
.fc-actions { display: flex; gap: 8px; }
.fc-actions .el-button { flex: 1; }
.page-header {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;
}
.page-header h1 { font-size: 22px; color: #3d2314; font-weight: 700; }
.header-actions { display: flex; gap: 12px; align-items: center; }

.fam-code {
  background: #f0e6dc; padding: 2px 8px; border-radius: 6px;
  color: #8b6f5c; font-size: 13px; letter-spacing: 1px;
}
.fam-name-link { color: #3d2314; font-weight: 600; }

.crumb {
  display: flex; align-items: center; gap: 8px;
  margin-bottom: 16px; font-size: 15px;
}
.crumb-back { color: #FF6B35; cursor: pointer; font-weight: 600; }
.crumb-back:hover { text-decoration: underline; }
.crumb-sep { color: #c4a882; }
.crumb-now { color: #3d2314; font-weight: 700; }

/* 未分组用户 */
.orphan-section {
  margin-top: 24px;
  background: white;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
}
.orphan-title {
  font-size: 15px; font-weight: 700; color: #3d2314;
  margin-bottom: 14px; display: flex; align-items: center; gap: 8px;
}
.orphan-count { font-size: 13px; color: #c4a882; font-weight: 400; }
.orphan-list { display: flex; flex-direction: column; gap: 8px; }
.orphan-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; background: #faf8f5; border-radius: 10px;
  font-size: 14px;
}
.oi-emoji { font-size: 16px; }
.oi-name { font-weight: 600; color: #3d2314; flex: 1; }
.oi-id { font-size: 12px; color: #c4a882; }
</style>
