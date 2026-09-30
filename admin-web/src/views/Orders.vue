<template>
  <div class="orders-page">
    <div class="page-header">
      <h1>📋 订单管理</h1>
      <div class="header-actions">
        <el-button-group>
          <el-button :type="mode === 'today' ? 'primary' : 'default'" @click="setMode('today')">今日</el-button>
          <el-button :type="mode === 'all' ? 'primary' : 'default'" @click="setMode('all')">全部</el-button>
        </el-button-group>
        <el-button type="success" @click="loadOrders" :loading="loading">刷新</el-button>
      </div>
    </div>

    <!-- 统计（异步子组件） -->
    <OrderStats :orders="orders" />

    <!-- 订单卡片列表 -->
    <div v-if="loading && orders.length === 0" class="empty">
      <div class="empty-icon">⏳</div>
      <div>加载中...</div>
    </div>

    <div v-else-if="orders.length === 0" class="empty">
      <div class="empty-icon">🍽️</div>
      <div>{{ mode === 'today' ? '今日还没有订单' : '还没有任何订单' }}</div>
    </div>

    <div v-else class="orders-grid">
      <OrderCard
        v-for="o in orders"
        :key="o.orderId"
        :order="o"
        :deleting="deletingId === o.orderId"
        @open="openOrder"
        @edit="openEdit"
        @delete="onDeleteOrder"
      />
    </div>

    <!-- 订单详情弹窗（异步子组件） -->
    <OrderDialog
      v-model:visible="detailVisible"
      :loading="detailLoading"
      :detail="detail"
      :can-operate="canOperate"
      :confirming-all="confirmingAll"
      :confirming-item="confirmingItem"
      :rejecting-item="rejectingItem"
      @close="onDetailClose"
      @confirm-all="onConfirmAll"
      @confirm-item="onConfirmItem"
      @reject-item="onRejectItem"
    />

    <!-- 修改订单弹窗 -->
    <el-dialog
      v-model="editVisible"
      :width="windowWidth < 768 ? '95%' : '480px'"
      :fullscreen="windowWidth < 480"
      :show-close="true"
      title="✏️ 修改订单"
    >
      <div v-if="editForm" class="edit-form">
        <div class="ef-info">
          📦 订单 #{{ editForm.orderId }} · 👤 {{ editForm.userNickname }} · ¥{{ formatMoney(editForm.totalAmount) }}
        </div>
        <el-form label-width="80px" label-position="left">
          <el-form-item label="下单者">
            <el-select
              v-model="editForm.userId"
              filterable
              placeholder="选择本家庭成员"
              style="width:100%"
            >
              <el-option
                v-for="m in familyMembers"
                :key="m.userId"
                :label="m.nickname"
                :value="m.userId"
              />
            </el-select>
            <div style="color:#c4a882;font-size:12px;margin-top:4px">只能选择当前家庭内的成员</div>
          </el-form-item>
          <el-form-item label="订单价格">
            <el-input-number
              v-model="editForm.totalAmount"
              :min="0"
              :precision="2"
              :step="1"
              style="width:100%"
            />
            <div style="color:#c4a882;font-size:12px;margin-top:4px">单位：元</div>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="editForm.remark" type="textarea" :rows="3" maxlength="255" show-word-limit placeholder="订单备注（可留空）" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="onSaveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, defineAsyncComponent } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTodayOrders, getOrders, getOrderDetail,
  confirmOrder, confirmOrderItem, rejectOrderItem,
  updateOrder, deleteOrder, getAdminFamiliesOverview
} from '../api'
import { getSecureItem } from '../utils/secureStore'

// 按需异步加载子组件（拆分：统计 / 订单卡 / 详情弹窗）
const OrderStats = defineAsyncComponent(() => import('../components/OrderStats.vue'))
const OrderCard = defineAsyncComponent(() => import('../components/OrderCard.vue'))
const OrderDialog = defineAsyncComponent(() => import('../components/OrderDialog.vue'))

const loading = ref(false)
const orders = ref([])
const mode = ref('today') // 'today' | 'all'

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const confirmingAll = ref(false)
const confirmingItem = ref(null)
const rejectingItem = ref(null)
const editVisible = ref(false)
const editForm = ref(null)
const editSaving = ref(false)
const familyMembers = ref([])
const deletingId = ref(null)
const windowWidth = ref(window.innerWidth)
const handleResize = () => { windowWidth.value = window.innerWidth }

const canOperate = computed(() => detail.value && detail.value.canConfirm === true)

const formatMoney = (n) => parseFloat(n || 0).toFixed(2)

const setMode = (m) => {
  if (mode.value === m) return
  mode.value = m
  loadOrders()
}

const loadOrders = async () => {
  loading.value = true
  try {
    const list = mode.value === 'today' ? await getTodayOrders() : await getOrders()
    orders.value = list || []
  } catch (e) {
    orders.value = []
  } finally {
    loading.value = false
  }
}

const openOrder = async (orderId) => {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getOrderDetail(orderId)
  } catch (e) {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

const refreshDetail = async () => {
  if (!detail.value) return
  try {
    detail.value = await getOrderDetail(detail.value.orderId)
  } catch (e) {}
}

// 详情弹窗关闭后刷新订单列表，让订单状态与菜品的最新状态同步
const onDetailClose = () => {
  loadOrders()
}

const onConfirmItem = async (itemId) => {
  confirmingItem.value = itemId
  try {
    await confirmOrderItem(itemId)
    ElMessage.success('已确认')
    await refreshDetail()
  } catch (e) {} finally {
    confirmingItem.value = null
  }
}

const onRejectItem = async (itemId) => {
  try {
    await ElMessageBox.confirm('驳回这道菜？', '驳回', { type: 'warning' })
  } catch (e) { return }
  rejectingItem.value = itemId
  try {
    await rejectOrderItem(itemId)
    ElMessage.success('已驳回')
    await refreshDetail()
  } catch (e) {} finally {
    rejectingItem.value = null
  }
}

const onConfirmAll = async () => {
  try {
    await ElMessageBox.confirm('一键确认整笔订单的所有菜品？', '一键确认', { type: 'info' })
  } catch (e) { return }
  confirmingAll.value = true
  try {
    await confirmOrder(detail.value.orderId)
    ElMessage.success('已一键确认')
    await refreshDetail()
    loadOrders()
  } catch (e) {} finally {
    confirmingAll.value = false
  }
}

// ====== 修改订单 ======
const openEdit = async (o) => {
  editForm.value = {
    orderId: o.orderId,
    userId: o.userId,
    userNickname: o.userNickname,
    totalAmount: parseFloat(o.totalAmount) || 0,
    remark: o.remark || ''
  }
  editVisible.value = true
  loadFamilyMembers()
}

// 加载当前家庭成员（用于下单者下拉，只允许本家庭成员）
const loadFamilyMembers = async () => {
  familyMembers.value = []
  try {
    const currentFamilyId = await getSecureItem('admin_family_id')
    const fams = await getAdminFamiliesOverview()
    const fam = fams.find(f => String(f.familyId) === String(currentFamilyId))
    if (fam && fam.members) {
      familyMembers.value = fam.members.map(m => ({
        userId: m.userId,
        nickname: m.nickname || `用户#${m.userId}`,
        role: m.role
      }))
    }
  } catch (e) {}
}

const onSaveEdit = async () => {
  const f = editForm.value
  if (!f) return
  if (!f.userId) {
    ElMessage.warning('请选择下单者')
    return
  }
  editSaving.value = true
  try {
    await updateOrder(f.orderId, { userId: f.userId, totalAmount: f.totalAmount, remark: f.remark })
    ElMessage.success('订单已更新')
    editVisible.value = false
    loadOrders()
    if (detail.value && detail.value.orderId === f.orderId) {
      await refreshDetail()
    }
  } catch (e) {} finally {
    editSaving.value = false
  }
}

// ====== 删除订单 ======
const onDeleteOrder = async (o) => {
  try {
    await ElMessageBox.confirm(
      `确定删除订单 #${o.orderId}（${o.userNickname || '未知用户'} · ¥${formatMoney(o.totalAmount)}）？\n订单及其所有菜品将一并删除，不可恢复。`,
      '删除订单',
      { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' }
    )
  } catch (e) { return }
  deletingId.value = o.orderId
  try {
    await deleteOrder(o.orderId)
    ElMessage.success('订单已删除')
    if (detail.value && detail.value.orderId === o.orderId) {
      detailVisible.value = false
    }
    loadOrders()
  } catch (e) {} finally {
    deletingId.value = null
  }
}

onMounted(() => {
  loadOrders()
  window.addEventListener('admin-family-changed', loadOrders)
  window.addEventListener('family-changed', loadOrders)
  window.addEventListener('resize', handleResize)
})
onUnmounted(() => {
  window.removeEventListener('admin-family-changed', loadOrders)
  window.removeEventListener('family-changed', loadOrders)
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.orders-page { padding: 24px; max-width: 1200px; margin: 0 auto; }

.page-header {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;
}
.page-header h1 { font-size: 22px; color: #3d2314; font-weight: 700; margin: 0; }
.header-actions { display: flex; gap: 12px; align-items: center; }

@media (max-width: 767px) {
  .orders-page { padding: 12px; }
  .page-header { flex-wrap: wrap; gap: 8px; }
  .page-header h1 { font-size: 18px; }
  .orders-grid { grid-template-columns: 1fr; gap: 12px; }
}

/* 订单卡片网格 */
.orders-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px;
}

.edit-form { padding: 8px 4px; }
.ef-info { font-size: 13px; color: #666;
  background: #f7f8fa; border-radius: 8px;
  padding: 10px 12px; margin-bottom: 16px; }

/* 空状态 */
.empty { text-align: center; padding: 80px 20px; color: #8b6f5c; font-size: 14px;
  background: white; border-radius: 16px; }
.empty-icon { font-size: 48px; margin-bottom: 12px; }
</style>
