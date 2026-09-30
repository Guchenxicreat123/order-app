<template>
  <div class="family-users">
    <!-- 家庭信息条 -->
    <div class="fam-bar">
      <div class="fam-info">
        <div class="fam-title">🏠 {{ family.name }}</div>
        <div class="fam-sub">
          加入码 <code>{{ family.code }}</code>
          · 成员 {{ family.memberCount }} 人
          · 创建者 {{ family.ownerName || '-' }}
          · 主厨 {{ family.chefName || '-' }}
        </div>
      </div>
      <el-button type="success" plain @click="openAdd">＋ 加入用户</el-button>
    </div>

    <!-- 用户一览：桌面表格 -->
    <el-table v-if="!isMobile" :data="filteredMembers" :loading="loading" stripe style="width:100%">
      <el-table-column label="ID" prop="userId" width="180">
        <template #default="{ row }">
          <code class="u-id">{{ row.userId }}</code>
        </template>
      </el-table-column>
      <el-table-column label="用户" min-width="150">
        <template #default="{ row }">
          <span class="u-avatar">{{ row.nickname ? row.nickname[0] : '?' }}</span>
          <span class="u-name">{{ row.nickname }}</span>
        </template>
      </el-table-column>
      <el-table-column label="角色" width="110">
        <template #default="{ row }">
          <el-tag :type="row.role === 'OWNER' ? 'danger' : (row.role === 'CHEF' ? 'warning' : 'info')" size="small">
            {{ roleText(row.role) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="加入时间" min-width="120">
        <template #default="{ row }">{{ fmtDate(row.joinedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="230" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.role !== 'OWNER'"
            size="small" type="warning" plain @click="openRole(row)"
          >调角色</el-button>
          <el-button
            v-if="row.role !== 'OWNER'"
            size="small" type="danger" plain @click="openRemove(row)"
          >移出</el-button>
          <el-button size="small" type="danger" plain @click="openDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 用户一览：移动端卡片 -->
    <div v-if="isMobile" class="fu-cards">
      <div
        v-for="row in filteredMembers"
        :key="row.userId"
        class="fu-card"
      >
        <div class="fu-row">
          <div class="fu-user">
            <span class="u-avatar">{{ row.nickname ? row.nickname[0] : '?' }}</span>
            <span class="u-name">{{ row.nickname }}</span>
            <el-tag :type="row.role === 'OWNER' ? 'danger' : (row.role === 'CHEF' ? 'warning' : 'info')" size="small">
              {{ roleText(row.role) }}
            </el-tag>
          </div>
          <span class="fu-time">{{ fmtDate(row.joinedAt) }}</span>
        </div>
        <div class="fu-actions">
          <el-button v-if="row.role !== 'OWNER'" size="small" type="warning" plain @click="openRole(row)">调角色</el-button>
          <el-button v-if="row.role !== 'OWNER'" size="small" type="danger" plain @click="openRemove(row)">移出</el-button>
          <el-button size="small" type="danger" plain @click="openDelete(row)">删除</el-button>
        </div>
      </div>
    </div>
    <el-empty v-if="!loading && filteredMembers.length === 0" description="该家庭还没有成员" />

    <!-- 加入用户（选择全站用户，固定加入本家庭） -->
    <el-dialog v-model="addVisible" title="加入用户" :width="isMobile ? '90%' : '480px'">
      <el-form label-width="90px">
        <el-form-item label="目标家庭">
          <span>{{ family.name }}（{{ family.code }}）</span>
        </el-form-item>
        <el-form-item label="选择用户">
          <el-select
            v-model="addUserId"
            filterable
            placeholder="搜索昵称 / ID"
            style="width:100%"
          >
            <el-option
              v-for="u in allUsers"
              :key="u.userId"
              :disabled="u.families && u.families.some(f => f.familyId === family.familyId)"
              :label="`${u.nickname || '(未命名)'} (#${u.userId})${isInFamily(u) ? ' — 已是成员' : ''}`"
              :value="u.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="addRole">
            <el-radio label="MEMBER">成员</el-radio>
            <el-radio label="CHEF">主厨</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAdd">确定</el-button>
      </template>
    </el-dialog>

    <!-- 调整角色 -->
    <el-dialog v-model="roleVisible" title="调整角色" :width="isMobile ? '90%' : '440px'">
      <el-form label-width="90px">
        <el-form-item label="用户">
          <span>{{ current && current.nickname }} (#{{ current && current.userId }})</span>
        </el-form-item>
        <el-form-item label="家庭">
          <span>{{ family.name }}</span>
        </el-form-item>
        <el-form-item label="新角色">
          <el-radio-group v-model="roleTarget">
            <el-radio label="MEMBER">成员</el-radio>
            <el-radio label="CHEF">主厨</el-radio>
          </el-radio-group>
        </el-form-item>
        <p v-if="roleTarget === 'CHEF'" style="color:#e6a23c;font-size:12px">
          提示：设为主厨会把该家庭原主厨降为成员（每家庭仅一位主厨）。
        </p>
      </el-form>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitRole">确定</el-button>
      </template>
    </el-dialog>

    <!-- 移出家庭 -->
    <el-dialog v-model="removeVisible" title="移出家庭" :width="isMobile ? '90%' : '440px'">
      <div style="padding: 8px 0; color: #3d2314;">
        <p style="margin: 0;">确认将 <strong>{{ current && current.nickname }}</strong> (#{{ current && current.userId }}) 移出「{{ family.name }}」吗？</p>
      </div>
      <template #footer>
        <el-button @click="removeVisible = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="submitRemove">移出</el-button>
      </template>
    </el-dialog>

    <!-- 删除用户（全局删除） -->
    <el-dialog v-model="deleteVisible" title="删除用户" :width="isMobile ? '90%' : '460px'">
      <div style="padding: 8px 0; color: #3d2314;">
        <p style="margin: 0 0 12px;">确定要删除用户 <strong>{{ current && current.nickname }}</strong> (#{{ current && current.userId }}) 吗？</p>
        <p style="margin: 0; color: #c0392b; font-size: 13px;">
          ⚠️ 此操作<strong>不可恢复</strong>，会连带删除：
        </p>
        <ul style="margin: 4px 0 0; padding-left: 18px; color: #c0392b; font-size: 13px;">
          <li><strong>账号本身</strong>，以及它在所有家庭的成员关系</li>
          <li>它的<strong>历史订单</strong>和订单里的菜</li>
          <li>它的<strong>推送记录</strong>、购物车、当日菜单残留</li>
        </ul>
        <p style="margin: 8px 0 0; color: #8a6d3b; font-size: 13px;">
          如果只是想把它从「当前家庭」里去掉，请改用<strong>「移出家庭」</strong>。
        </p>
        <p style="margin: 8px 0 0; color: #8a6d3b; font-size: 13px;">
          如果该用户是某个家庭的<strong>创建者</strong>，需先在「家庭管理」里删除或转移该家庭。
        </p>
      </div>
      <template #footer>
        <el-button @click="deleteVisible = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="submitDelete">确认删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAdminUsers, adminAddFamily, adminRemoveFamily, adminSetRole, adminDeleteUser
} from '../api'

const props = defineProps({
  family: { type: Object, required: true },
})
const emit = defineEmits(['changed'])

const loading = ref(false)
const isMobile = ref(window.innerWidth < 768)
const handleResize = () => { isMobile.value = window.innerWidth < 768 }
const saving = ref(false)
const members = ref([])
const allUsers = ref([])
const current = ref(null)
const keyword = ref('')

// add dialog
const addVisible = ref(false)
const addUserId = ref(null)
const addRole = ref('MEMBER')
// role dialog
const roleVisible = ref(false)
const roleTarget = ref('MEMBER')
// remove dialog
const removeVisible = ref(false)
// delete dialog
const deleteVisible = ref(false)

const filteredMembers = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return members.value
  return members.value.filter(m =>
    (m.nickname || '').toLowerCase().includes(kw) ||
    String(m.userId).includes(kw)
  )
})

function roleText(r) {
  return r === 'OWNER' ? '创建者' : (r === 'CHEF' ? '主厨' : '成员')
}
function isInFamily(u) {
  return u.families && u.families.some(f => f.familyId === props.family.familyId)
}
function fmtDate(s) {
  if (!s) return '-'
  const d = new Date(s)
  if (isNaN(d)) return '-'
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

const syncMembers = async () => {
  loading.value = true
  try {
    members.value = [...(props.family.members || [])].sort((a, b) => {
      const rank = { OWNER: 0, CHEF: 1, MEMBER: 2 }
      // userId 是 openid 长串，不能用减法（会得到 NaN 导致排序失效）
      return (rank[a.role] ?? 3) - (rank[b.role] ?? 3)
        || String(a.userId).localeCompare(String(b.userId))
    })
  } finally {
    loading.value = false
  }
}

/**
 * 父组件重新拉取家庭数据后会换掉 family 对象（组件 key 没变，不会重建），
 * 这里必须跟着刷新本地副本 —— 否则刚删掉/加入/改角色的成员会一直留在画面上，
 * 必须手动刷新页面才消失。
 */
watch(() => props.family && props.family.members, syncMembers, { deep: true })

const reload = async () => {
  await syncMembers()
  // 成员发生了变化，顺手刷新"可加入用户"下拉里的候选（否则被删掉的用户还留在候选里，
  // 再选他加入家庭会报"用户不存在"）
  await loadAllUsers()
  emit('changed')
}

const loadAllUsers = async () => {
  try {
    allUsers.value = await getAdminUsers() || []
  } catch (e) {
    allUsers.value = []
  }
}

function openAdd() {
  addUserId.value = null
  addRole.value = 'MEMBER'
  addVisible.value = true
  if (allUsers.value.length === 0) loadAllUsers()
}
async function submitAdd() {
  if (!addUserId.value) { ElMessage.warning('请选择用户'); return }
  saving.value = true
  try {
    await adminAddFamily(addUserId.value, { familyId: props.family.familyId, role: addRole.value })
    ElMessage.success('已加入')
    addVisible.value = false
    await reload()
  } catch (e) {} finally { saving.value = false }
}

function openRole(row) {
  current.value = row
  roleTarget.value = 'MEMBER'
  roleVisible.value = true
}
async function submitRole() {
  saving.value = true
  try {
    await adminSetRole(current.value.userId, { familyId: props.family.familyId, role: roleTarget.value })
    ElMessage.success('角色已更新')
    roleVisible.value = false
    await reload()
  } catch (e) {} finally { saving.value = false }
}

function openRemove(row) {
  current.value = row
  removeVisible.value = true
}
async function submitRemove() {
  try {
    await ElMessageBox.confirm(`确认将该用户移出「${props.family.name}」吗？`, '确认', { type: 'warning' })
  } catch (e) { return }
  saving.value = true
  try {
    await adminRemoveFamily(current.value.userId, { familyId: props.family.familyId })
    ElMessage.success('已移出')
    removeVisible.value = false
    await reload()
  } catch (e) {} finally { saving.value = false }
}

function openDelete(row) {
  current.value = row
  deleteVisible.value = true
}
async function submitDelete() {
  saving.value = true
  try {
    await adminDeleteUser(current.value.userId)
    ElMessage.success('用户已删除')
    deleteVisible.value = false
    await reload()
  } catch (e) {} finally { saving.value = false }
}

onMounted(syncMembers)
window.addEventListener('resize', handleResize)
onUnmounted(() => window.removeEventListener('resize', handleResize))
</script>

<style scoped>
.family-users { width: 100%; }
@media (max-width: 767px) {
  .fam-bar { flex-direction: column; align-items: stretch; gap: 12px; }
  .fam-info { width: 100%; }
  .fam-bar .el-button { width: 100%; }
}
.fu-cards { display: flex; flex-direction: column; gap: 12px; }
.fu-card { background: white; border-radius: 12px; padding: 16px; box-shadow: 0 2px 8px rgba(0,0,0,0.05); }
.fu-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; gap: 8px; }
.fu-user { display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0; }
.fu-time { font-size: 12px; color: #8b6f5c; flex-shrink: 0; }
.fu-actions { display: flex; gap: 8px; }
.fu-actions .el-button { flex: 1; padding: 8px 4px !important; }
.fam-bar {
  display: flex; justify-content: space-between; align-items: center;
  background: #fff; border-radius: 12px;
  padding: 16px 20px; margin-bottom: 16px;
  border: 1px solid #f0e6dc;
}
.fam-title { font-size: 18px; font-weight: 700; color: #3d2314; }
.fam-sub { font-size: 12px; color: #8b6f5c; margin-top: 6px; }
.fam-sub code {
  background: #f0e6dc; padding: 1px 6px; border-radius: 4px; color: #8b6f5c;
}
.u-avatar {
  display: inline-flex; align-items: center; justify-content: center;
  width: 28px; height: 28px; border-radius: 50%;
  background: #f0e6dc; color: #8b6f5c; font-size: 13px; margin-right: 8px;
}
.u-name { color: #3d2314; font-weight: 500; }
.u-id {
  /* openid 是长串字符，避免单元格内换行撑高行高 */
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  color: #6b5544;
  background: #faf6f1;
  padding: 2px 8px;
  border-radius: 4px;
  white-space: nowrap;
  word-break: keep-all;
}
</style>
