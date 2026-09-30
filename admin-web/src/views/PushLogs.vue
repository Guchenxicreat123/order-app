<template>
  <div class="push-logs-page" :class="{ mobile: isMobile }">
    <div class="page-header">
      <h1>📲 推送记录</h1>
      <div class="header-actions">
        <el-button type="success" :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <!-- 统计卡片：今日总量 / 失败 / 队列 / dry-run 占比 -->
    <div class="stats-row" v-if="stats">
      <div class="stat-card">
        <div class="stat-label">📨 今日推送</div>
        <div class="stat-value">{{ stats.todayTotal }}</div>
        <div class="stat-sub">
          <span class="ok">✓ {{ stats.todayByType.NEW_ORDER.success + stats.todayByType.STATUS_CHANGED.success }} 成功</span>
          <span class="fail" v-if="stats.todayFailed > 0">✗ {{ stats.todayFailed }} 失败</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">🍳 新订单（主厨收）</div>
        <div class="stat-value">
          <span class="ok">{{ stats.todayByType.NEW_ORDER.success }}</span>
          <span class="sep">/</span>
          <span class="fail" v-if="stats.todayByType.NEW_ORDER.failed">{{ stats.todayByType.NEW_ORDER.failed }}</span>
          <span v-else class="ok">0</span>
        </div>
        <div class="stat-sub">成功 / 失败</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">📋 状态变更（下单人收）</div>
        <div class="stat-value">
          <span class="ok">{{ stats.todayByType.STATUS_CHANGED.success }}</span>
          <span class="sep">/</span>
          <span class="fail" v-if="stats.todayByType.STATUS_CHANGED.failed">{{ stats.todayByType.STATUS_CHANGED.failed }}</span>
          <span v-else class="ok">0</span>
        </div>
        <div class="stat-sub">成功 / 失败</div>
      </div>
      <div class="stat-card" :class="{ warn: stats.recent.dryRunPct > 50 }">
        <div class="stat-label">🧪 dry-run 占比</div>
        <div class="stat-value">{{ stats.recent.dryRunPct }}%</div>
        <div class="stat-sub">最近 {{ stats.recent.total }} 条</div>
      </div>
      <div class="stat-card" :class="{ warn: stats.pending > 0 }">
        <div class="stat-label">🕒 重试队列</div>
        <div class="stat-value">{{ stats.pending }}</div>
        <div class="stat-sub">待重发 / {{ stats.dead }} 已耗尽</div>
      </div>
    </div>

    <!-- 过滤栏 -->
    <div class="filter-bar">
      <el-select
        v-model="filters.familyId"
        placeholder="全部家庭"
        clearable
        filterable
        style="width:200px"
        @change="loadLogs"
      >
        <el-option
          v-for="f in families"
          :key="f.familyId"
          :label="`${f.name}（${f.familyId}）`"
          :value="f.familyId"
        />
      </el-select>
      <el-select v-model="filters.type" placeholder="全部类型" clearable style="width:140px" @change="loadLogs">
        <el-option label="🍳 新订单" value="NEW_ORDER" />
        <el-option label="📋 状态变更" value="STATUS_CHANGED" />
      </el-select>
      <el-select v-model="filters.success" placeholder="全部状态" clearable style="width:120px" @change="loadLogs">
        <el-option label="✓ 成功" :value="1" />
        <el-option label="✗ 失败" :value="0" />
      </el-select>
      <div class="filter-meta">
        共 {{ total }} 条
      </div>
    </div>

    <!-- 桌面端：表格 -->
    <el-table
      v-if="!isMobile"
      :data="logs"
      :loading="loading"
      row-key="id"
      style="width:100%"
      empty-text="暂无推送记录"
    >
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.type === 'NEW_ORDER'" type="warning" size="small" effect="plain">🍳 新订单</el-tag>
          <el-tag v-else-if="row.type === 'STATUS_CHANGED'" type="primary" size="small" effect="plain">📋 状态变更</el-tag>
          <el-tag v-else size="small" effect="plain">{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="收件人" min-width="160">
        <template #default="{ row }">
          <div class="recipient">
            <span class="r-nickname">{{ row.nickname }}</span>
            <span class="r-family">{{ row.familyName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="关联" min-width="180">
        <template #default="{ row }">
          <div class="link-info">
            <span v-if="row.dishName" class="l-dish">🍱 {{ row.dishName }}</span>
            <span v-if="row.orderId" class="l-order">
              <router-link :to="`/orders`" class="l-link">订单 #{{ row.orderId }}</router-link>
            </span>
            <span v-if="!row.dishName && !row.orderId" class="l-empty">—</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.success" type="success" size="small" effect="plain">✓ 成功</el-tag>
          <el-tag v-else type="danger" size="small" effect="plain">✗ 失败</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="原因 / 错误" min-width="220">
        <template #default="{ row }">
          <span v-if="row.success && !row.errorMsg" class="ok">—</span>
          <span v-else-if="row.errorMsg === 'dry-run'" class="dry-run">🧪 dry-run（仅记录未真发）</span>
          <span v-else class="err">{{ row.errorMsg }}</span>
        </template>
      </el-table-column>
      <el-table-column label="payload" min-width="160">
        <template #default="{ row }">
          <el-button v-if="row.payload" size="small" link @click="openPayload(row)">查看 JSON</el-button>
          <span v-else class="l-empty">—</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 移动端：卡片列表 -->
    <div v-else-if="logs.length > 0" class="log-cards">
      <div v-for="row in logs" :key="row.id" class="log-card" :class="{ fail: !row.success }">
        <div class="lc-head">
          <el-tag v-if="row.type === 'NEW_ORDER'" type="warning" size="small">🍳 新订单</el-tag>
          <el-tag v-else-if="row.type === 'STATUS_CHANGED'" type="primary" size="small">📋 状态变更</el-tag>
          <el-tag v-if="row.success" type="success" size="small">✓</el-tag>
          <el-tag v-else type="danger" size="small">✗</el-tag>
          <span class="lc-time">{{ fmtDateTime(row.createdAt) }}</span>
        </div>
        <div class="lc-body">
          <div class="lc-recipient">
            👤 {{ row.nickname }} · 🏠 {{ row.familyName }}
          </div>
          <div class="lc-link" v-if="row.dishName || row.orderId">
            <span v-if="row.dishName">🍱 {{ row.dishName }}</span>
            <span v-if="row.orderId">· 订单 #{{ row.orderId }}</span>
          </div>
          <div class="lc-err" v-if="!row.success || row.errorMsg">
            <span v-if="row.errorMsg === 'dry-run'" class="dry-run">🧪 dry-run</span>
            <span v-else-if="row.errorMsg" class="err">✗ {{ row.errorMsg }}</span>
          </div>
          <el-button v-if="row.payload" size="small" link @click="openPayload(row)">查看 payload</el-button>
        </div>
      </div>
    </div>

    <div v-else-if="!loading" class="empty">
      <div class="empty-icon">📲</div>
      <div>还没有任何推送记录</div>
      <div class="empty-hint">小程序触发「下单 / 主厨确认 / 驳回」后会在这里出现一条记录</div>
    </div>

    <!-- 加载中 -->
    <div v-if="loading && logs.length === 0" class="empty">
      <div class="empty-icon">⏳</div>
      <div>加载中…</div>
    </div>

    <!-- payload 详情弹窗 -->
    <el-dialog v-model="payloadVisible" title="📦 推送 payload" width="560px" :modal="true" append-to-body>
      <div v-if="payloadRow" class="payload-meta">
        <div><b>类型</b> {{ payloadRow.type }} · <b>结果</b> {{ payloadRow.success ? '✓' : '✗' }}</div>
        <div><b>收件人</b> {{ payloadRow.nickname }}（{{ payloadRow.familyName }}）</div>
        <div><b>时间</b> {{ fmtDateTime(payloadRow.createdAt) }}</div>
        <div v-if="payloadRow.errorMsg"><b>错误</b> {{ payloadRow.errorMsg }}</div>
      </div>
      <pre class="payload-json">{{ formatPayload(payloadRow && payloadRow.payload) }}</pre>
      <template #footer>
        <el-button @click="payloadVisible = false">关闭</el-button>
        <el-button type="primary" @click="copyPayload">复制 JSON</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getPushLogs, getPushStats, getAdminFamilies } from '../api'

const loading = ref(false)
const logs = ref([])
const total = ref(0)
const stats = ref(null)
const families = ref([])

const filters = reactive({
  familyId: '',
  type: '',
  success: '',
})

const isMobile = ref(window.innerWidth < 768)
window.addEventListener('resize', () => {
  isMobile.value = window.innerWidth < 768
})

const payloadVisible = ref(false)
const payloadRow = ref(null)

function openPayload(row) {
  payloadRow.value = row
  payloadVisible.value = true
}

function formatPayload(s) {
  if (!s) return '（空）'
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
  } catch (e) {
    return s
  }
}

async function copyPayload() {
  if (!payloadRow.value) return
  try {
    await navigator.clipboard.writeText(formatPayload(payloadRow.value.payload))
    ElMessage.success('已复制')
  } catch (e) {
    ElMessage.error('复制失败：' + (e.message || '未知错误'))
  }
}

function fmtDateTime(s) {
  if (!s) return '-'
  const d = new Date(s)
  if (isNaN(d)) return s
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

async function loadFamilies() {
  try {
    families.value = (await getAdminFamilies()) || []
  } catch (e) {
    families.value = []
  }
}

async function loadStats() {
  try {
    stats.value = await getPushStats()
  } catch (e) {
    stats.value = null
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const params = { limit: 100 }
    if (filters.familyId) params.familyId = filters.familyId
    if (filters.type) params.type = filters.type
    if (filters.success === 1 || filters.success === 0) params.success = filters.success
    const r = await getPushLogs(params)
    logs.value = (r && r.list) || []
    total.value = (r && r.total) || 0
  } catch (e) {
    logs.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadAll() {
  // 并发拉统计和列表（两者独立接口），失败互不影响
  await Promise.all([loadStats(), loadLogs()])
}

onMounted(() => {
  loadFamilies()
  loadAll()
})
</script>

<style scoped>
.push-logs-page {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
}
.push-logs-page.mobile { padding: 12px; }

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 8px;
}
.page-header h1 {
  font-size: 22px;
  font-weight: 700;
  color: #3d2314;
  margin: 0;
}

/* ============ 统计卡片 ============ */
.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  background: white;
  border-radius: 10px;
  padding: 14px 16px;
  border: 1px solid #f0e6dc;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.stat-card.warn {
  background: #fff8e1;
  border-color: #ffe082;
}
.stat-label {
  font-size: 12px;
  color: #8b6f5c;
  margin-bottom: 6px;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #3d2314;
  margin-bottom: 4px;
}
.stat-value .ok { color: #2e7d32; }
.stat-value .fail { color: #c62828; }
.stat-value .sep { color: #c4a882; margin: 0 4px; }
.stat-sub { font-size: 11px; color: #8b6f5c; }
.stat-sub .ok { color: #2e7d32; }
.stat-sub .fail { color: #c62828; margin-left: 8px; }

/* ============ 过滤栏 ============ */
.filter-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  background: white;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid #f0e6dc;
}
.filter-meta {
  margin-left: auto;
  font-size: 12px;
  color: #8b6f5c;
}

/* ============ 表格内 ============ */
.recipient { display: flex; flex-direction: column; gap: 2px; }
.r-nickname { font-weight: 600; color: #3d2314; }
.r-family { font-size: 12px; color: #8b6f5c; }
.link-info { display: flex; flex-direction: column; gap: 2px; }
.l-dish { font-size: 13px; color: #3d2314; }
.l-order { font-size: 12px; }
.l-link { color: #e67e22; text-decoration: none; }
.l-link:hover { text-decoration: underline; }
.l-empty { color: #c4a882; }
.ok { color: #2e7d32; }
.dry-run { color: #f57c00; font-weight: 500; }
.err { color: #c62828; font-size: 12px; word-break: break-all; }

/* ============ 移动端卡片 ============ */
.log-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.log-card {
  background: white;
  border-radius: 10px;
  padding: 12px 14px;
  border: 1px solid #f0e6dc;
}
.log-card.fail { border-left: 3px solid #c62828; }
.lc-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.lc-time {
  margin-left: auto;
  font-size: 11px;
  color: #8b6f5c;
}
.lc-body { display: flex; flex-direction: column; gap: 4px; }
.lc-recipient { font-size: 13px; color: #3d2314; }
.lc-link { font-size: 12px; color: #8b6f5c; }
.lc-err { font-size: 12px; }

/* ============ 空/加载 ============ */
.empty {
  text-align: center;
  padding: 60px 0;
  color: #8b6f5c;
}
.empty-icon { font-size: 48px; margin-bottom: 8px; }
.empty-hint { font-size: 12px; color: #c4a882; margin-top: 8px; }

/* ============ payload 弹窗 ============ */
.payload-meta {
  font-size: 12px;
  color: #8b6f5c;
  margin-bottom: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.payload-meta b { color: #3d2314; }
.payload-json {
  background: #2b1b12;
  color: #f8f0e3;
  padding: 12px 14px;
  border-radius: 8px;
  font-size: 12px;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow-y: auto;
  margin: 0;
}
</style>