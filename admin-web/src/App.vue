<template>
  <div class="layout" :class="{ mobile: isMobile }">
    <!-- 桌面端侧边栏 -->
    <aside class="sidebar" v-if="route.path !== '/login' && !isMobile">
      <div class="sidebar-header">
        <div class="logo">🍳 主厨后台</div>
        <div class="chef-name">{{ chefNickname }}</div>
      </div>
      <div class="family-switcher" v-if="families.length > 0">
        <div class="fs-label">🏠 当前家庭</div>
        <button class="family-current" @click="openPicker">
          <span class="fc-icon">🏠</span>
          <span class="fc-name">{{ currentFamilyName }}</span>
          <span class="fc-meta">{{ currentFamilyCount }}人</span>
          <span class="fc-caret">▾</span>
        </button>
      </div>
      <nav class="sidebar-nav">
        <router-link to="/orders" class="nav-item">
          <span class="nav-icon">📋</span> 订单管理
        </router-link>
        <router-link to="/dishes" class="nav-item">
          <span class="nav-icon">🍱</span> 菜单管理
        </router-link>
        <router-link to="/ingredients" class="nav-item">
          <span class="nav-icon">🧂</span> 配菜管理
        </router-link>
      </nav>
      <div class="sidebar-footer">
        <div class="footer-nav">
          <router-link to="/public-menu" class="nav-item">
            <span class="nav-icon">🌐</span> 公共菜单
          </router-link>
          <router-link to="/public-ingredients" class="nav-item">
            <span class="nav-icon">🥬</span> 公共配菜
          </router-link>
          <router-link to="/families" class="nav-item">
            <span class="nav-icon">🏠</span> 家庭管理
          </router-link>
          <router-link to="/push-logs" class="nav-item">
            <span class="nav-icon">📲</span> 推送记录
          </router-link>
        </div>
        <button class="logout-btn" @click="openPwdDialog">修改密码</button>
        <button class="logout-btn" @click="logout">退出登录</button>
      </div>
    </aside>

    <!-- 移动端顶部栏（仅登录后） -->
    <header class="mobile-header" v-if="route.path !== '/login' && isMobile">
      <button class="hamburger" @click="drawerOpen = true">☰</button>
      <div class="mobile-title">{{ pageTitle }}</div>
      <div class="mobile-actions">
        <el-dropdown
          v-if="families.length > 0"
          trigger="click"
          @command="onSwitchFamily"
        >
          <span class="mobile-family">🏠 {{ currentFamilyName }}</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="f in families"
                :key="f.familyId"
                :command="f.familyId"
              >{{ f.name }}（{{ f.memberCount }}人）</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- 家庭选择器弹窗 -->
    <el-dialog
      v-model="pickerVisible"
      title="🏠 选择家庭"
      width="560px"
      :modal="true"
      append-to-body
      @open="onPickerOpen"
    >
      <div class="picker-body">
        <el-input
          v-model="pickerQuery"
          placeholder="🔍 搜索家庭名称或编号..."
          clearable
          size="large"
          class="picker-search"
        />
        <div class="picker-list">
          <div
            v-for="f in filteredFamilies"
            :key="f.familyId"
            class="picker-item"
            :class="{ active: String(f.familyId) === String(currentFamily) }"
            @click="onPickFamily(f.familyId)"
          >
            <span class="pi-emoji">🏠</span>
            <span class="pi-name">{{ f.name }}</span>
            <code class="pi-code">{{ f.code }}</code>
            <span class="pi-meta">{{ f.memberCount || 0 }}人</span>
            <span v-if="String(f.familyId) === String(currentFamily)" class="pi-check">✓</span>
          </div>
          <div v-if="filteredFamilies.length === 0" class="picker-empty">
            没有找到匹配的家庭
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 移动端抽屉菜单 -->
    <el-drawer
      v-if="isMobile"
      v-model="drawerOpen"
      direction="ltr"
      :with-header="false"
      size="78%"
      :modal="true"
      class="mobile-drawer"
    >
      <div class="drawer-content">
        <div class="drawer-header">
          <div class="drawer-logo">🍳 主厨后台</div>
          <div class="drawer-chef">{{ chefNickname }}</div>
        </div>
        <nav class="drawer-nav" @click="drawerOpen = false">
          <router-link to="/orders" class="drawer-item">
            <span class="drawer-icon">📋</span> 订单管理
          </router-link>
          <router-link to="/dishes" class="drawer-item">
            <span class="drawer-icon">🍱</span> 菜单管理
          </router-link>
          <router-link to="/ingredients" class="drawer-item">
            <span class="drawer-icon">🧂</span> 配菜管理
          </router-link>
          <router-link to="/families" class="drawer-item">
            <span class="drawer-icon">🏠</span> 家庭管理
          </router-link>
          <router-link to="/push-logs" class="drawer-item">
            <span class="drawer-icon">📲</span> 推送记录
          </router-link>
          <router-link to="/public-menu" class="drawer-item">
            <span class="drawer-icon">🌐</span> 公共菜单
          </router-link>
          <router-link to="/public-ingredients" class="drawer-item">
            <span class="drawer-icon">🥬</span> 公共配菜
          </router-link>
        </nav>
        <div class="drawer-footer">
          <button class="drawer-logout" @click="openPwdDialog">修改密码</button>
          <button class="drawer-logout" @click="logout">退出登录</button>
        </div>
      </div>
    </el-drawer>

    <!-- 主内容 -->
    <main class="main-content" style="background:#faf8f5;min-height:100vh">
      <router-view />
    </main>

    <!-- 修改管理员密码 -->
    <el-dialog v-model="pwdVisible" title="🔑 修改管理员密码" width="420px" append-to-body>
      <el-form label-width="90px" @submit.prevent>
        <el-form-item label="原密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="当前登录密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 4 位" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再输一次" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="submitPwd">保存</el-button>
      </template>
    </el-dialog>

    <!-- 移动端底部 tab 栏 -->
    <nav class="mobile-tabbar" v-if="route.path !== '/login' && isMobile">
      <router-link to="/orders" class="tab-item">
        <span class="tab-icon">📋</span>
        <span class="tab-label">订单</span>
      </router-link>
      <router-link to="/dishes" class="tab-item">
        <span class="tab-icon">🍱</span>
        <span class="tab-label">菜单</span>
      </router-link>
      <router-link to="/ingredients" class="tab-item">
        <span class="tab-icon">🧂</span>
        <span class="tab-label">配菜</span>
      </router-link>
      <router-link to="/families" class="tab-item">
        <span class="tab-icon">🏠</span>
        <span class="tab-label">家庭</span>
      </router-link>
      <router-link to="/public-menu" class="tab-item">
        <span class="tab-icon">🌐</span>
        <span class="tab-label">公共</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAdminFamilies, chefLogout, changeAdminPassword } from './api'
import {
  getSecureItem, setSecureItem, removeSecureItem,
  primeTokenCache, cachedToken, clearTokenCache
} from './utils/secureStore'

const route = useRoute()
const router = useRouter()
// 异步初始化（encrypt 后从 storage 读出来），避免 XSS 直接拿明文
const chefNickname = ref('主厨')
const families = ref([])
const famLoading = ref(false)
const currentFamily = ref(null)
const drawerOpen = ref(false)

const isMobile = ref(window.innerWidth < 768)

const pageTitle = computed(() => {
  const map = {
    '/orders': '订单管理',
    '/dishes': '菜单管理',
    '/ingredients': '配菜管理',
    '/families': '家庭管理',
    '/push-logs': '推送记录',
    '/public-menu': '公共菜单',
    '/public-ingredients': '公共配菜'
  }
  return map[route.path] || '主厨后台'
})

const currentFamilyName = computed(() => {
  const f = families.value.find(x => String(x.familyId) === String(currentFamily.value))
  return f ? f.name : '家庭'
})

const currentFamilyCount = computed(() => {
  const f = families.value.find(x => String(x.familyId) === String(currentFamily.value))
  return f ? (f.memberCount || 0) : 0
})

// ============ 家庭选择器（弹窗） ============
const pickerVisible = ref(false)
const pickerQuery = ref('')

const openPicker = () => {
  pickerQuery.value = ''
  pickerVisible.value = true
}

const onPickerOpen = () => {
  pickerQuery.value = ''
}

const filteredFamilies = computed(() => {
  const q = pickerQuery.value.trim().toLowerCase()
  if (!q) return families.value
  return families.value.filter(f =>
    String(f.name || '').toLowerCase().includes(q) ||
    String(f.code || '').toLowerCase().includes(q) ||
    String(f.familyId).includes(q)
  )
})

const onPickFamily = (familyId) => {
  pickerVisible.value = false
  if (String(familyId) === String(currentFamily.value)) return
  onSwitchFamily(familyId)
}

const handleResize = () => {
  isMobile.value = window.innerWidth < 768
}

const loadFamilies = async () => {
  famLoading.value = true
  try {
    const list = await getAdminFamilies() || []
    families.value = list
    window.__adminFamilies = list  // 共享给子页面
    if (list.length > 0) {
      if (!currentFamily.value || !list.some(f => String(f.familyId) === String(currentFamily.value))) {
        const def = list[0]
        currentFamily.value = def.familyId
        await setSecureItem('admin_family_id', String(def.familyId))
        primeTokenCache('admin_family_id')
      } else {
        currentFamily.value = Number(currentFamily.value)
      }
    }
  } catch (e) {
    families.value = []
  } finally {
    famLoading.value = false
  }
}

const onSwitchFamily = async (val) => {
  await setSecureItem('admin_family_id', String(val))
  primeTokenCache('admin_family_id')
  currentFamily.value = Number(val)
  window.dispatchEvent(new CustomEvent('admin-family-changed', { detail: val }))
}

const logout = async () => {
  await chefLogout()
  router.push('/login')
}

// ============ 修改密码（后台自助） ============
const pwdVisible = ref(false)
const pwdSaving = ref(false)
const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })

const openPwdDialog = () => {
  pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  pwdVisible.value = true
  drawerOpen.value = false
}

const submitPwd = async () => {
  const { oldPassword, newPassword, confirmPassword } = pwdForm.value
  if (!oldPassword) return ElMessage.warning('请输入原密码')
  if (!newPassword || newPassword.length < 4) return ElMessage.warning('新密码至少 4 位')
  if (newPassword !== confirmPassword) return ElMessage.warning('两次输入的新密码不一致')
  pwdSaving.value = true
  try {
    await changeAdminPassword(oldPassword, newPassword)
    pwdVisible.value = false
    ElMessage.success('密码已修改，请用新密码重新登录')
    await chefLogout()
    router.push('/login')
  } catch (e) {
    // 拦截器已弹错误提示
  } finally {
    pwdSaving.value = false
  }
}

onMounted(async () => {
  // 1. 异步 prime 加密 token 到内存缓存（axios 拦截器同步路径）
  const tok = await primeTokenCache('chef_token')
  const nick = await getSecureItem('chef_nickname')
  const fam = await getSecureItem('admin_family_id')
  if (nick) chefNickname.value = nick
  if (fam) currentFamily.value = Number(fam) || null
  if (tok) {
    loadFamilies()
  }
  window.addEventListener('families-changed', loadFamilies)
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app { height: 100%; }

.layout {
  display: flex;
  height: 100vh;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
.layout.mobile {
  flex-direction: column;
}

.sidebar {
  width: 220px;
  min-width: 220px;
  background: linear-gradient(180deg, #1a0e08 0%, #2B1B12 100%);
  display: flex;
  flex-direction: column;
  color: #f8f0e3;
}

.sidebar-header {
  padding: 28px 20px 20px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}
.family-switcher {
  padding: 16px 14px 6px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}
.fs-label {
  font-size: 12px;
  color: rgba(248,240,227,0.6);
  margin-bottom: 8px;
  padding-left: 2px;
}
.family-current {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.12);
  border-radius: 8px;
  color: #f8f0e3;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
  text-align: left;
}
.family-current:hover {
  background: rgba(255, 107, 53, 0.15);
  border-color: rgba(255, 107, 53, 0.4);
}
.fc-icon { font-size: 14px; }
.fc-name { flex: 1; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fc-meta { font-size: 11px; color: #c4a882; }
.fc-caret { color: #c4a882; font-size: 11px; }
.logo {
  font-size: 20px;
  font-weight: 700;
  color: #FF6B35;
  letter-spacing: 2px;
}
.chef-name {
  font-size: 12px;
  color: #c4a882;
  margin-top: 4px;
}
.sidebar-nav {
  flex: 1;
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 10px;
  color: #c4a882;
  text-decoration: none;
  font-size: 14px;
  transition: all 0.2s;
}
.nav-item:hover, .nav-item.router-link-active {
  background: rgba(255, 107, 53, 0.15);
  color: #FF6B35;
}
.nav-icon { font-size: 16px; }
.sidebar-footer {
  padding: 12px;
  border-top: 1px solid rgba(255,255,255,0.08);
}
.footer-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 10px;
}
.logout-btn {
  width: 100%;
  padding: 10px;
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.12);
  border-radius: 8px;
  color: #c4a882;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}
.logout-btn:hover {
  background: rgba(255, 107, 53, 0.2);
  color: #FF6B35;
  border-color: rgba(255, 107, 53, 0.4);
}
/* 两个 logout-btn 上下之间加大间距（仅第二个生效，不影响 .footer-nav 与第一个按钮之间的 10px） */
.logout-btn + .logout-btn {
  margin-top: 12px;
}

.main-content {
  flex: 1;
  overflow-y: auto;
  background: linear-gradient(180deg, #FFF9F0 0%, #f8f4f0 100%);
}

/* ============= 移动端样式 ============= */
.mobile-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: linear-gradient(180deg, #1a0e08 0%, #2B1B12 100%);
  color: white;
  position: sticky;
  top: 0;
  z-index: 50;
}
.hamburger {
  background: transparent;
  border: none;
  color: white;
  font-size: 28px;
  padding: 4px 10px;
  cursor: pointer;
  line-height: 1;
}
.mobile-title {
  flex: 1;
  font-size: 17px;
  font-weight: 600;
}
.mobile-actions {
  display: flex;
  align-items: center;
}
.mobile-family {
  font-size: 13px;
  color: #FF6B35;
  padding: 4px 10px;
  background: rgba(255,107,53,0.12);
  border-radius: 14px;
}

.layout.mobile .main-content {
  flex: 1;
  padding-bottom: 60px; /* 给底部 tab 留空 */
}

.mobile-tabbar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  background: white;
  border-top: 1px solid #f0e6dc;
  z-index: 50;
  padding-bottom: env(safe-area-inset-bottom, 0);
}
.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 4px;
  color: #8b6f5c;
  text-decoration: none;
  font-size: 11px;
}
.tab-item.router-link-active {
  color: #FF6B35;
}
.tab-icon { font-size: 22px; }
.tab-label { font-size: 11px; }

/* 抽屉内容 */
.drawer-content {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: linear-gradient(180deg, #1a0e08 0%, #2B1B12 100%);
  color: #f8f0e3;
}
.drawer-header {
  padding: 24px 20px 20px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}
.drawer-logo {
  font-size: 22px;
  font-weight: 700;
  color: #FF6B35;
  letter-spacing: 2px;
}
.drawer-chef {
  font-size: 13px;
  color: #c4a882;
  margin-top: 4px;
}
.drawer-nav {
  flex: 1;
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.drawer-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-radius: 12px;
  color: #c4a882;
  text-decoration: none;
  font-size: 16px;
  transition: all 0.2s;
}
.drawer-item:active {
  background: rgba(255, 107, 53, 0.2);
  color: #FF6B35;
}
.drawer-item.router-link-active {
  background: rgba(255, 107, 53, 0.15);
  color: #FF6B35;
}
.drawer-icon { font-size: 22px; }
.drawer-footer {
  padding: 20px;
  border-top: 1px solid rgba(255,255,255,0.08);
}
.drawer-logout {
  width: 100%;
  padding: 14px;
  background: rgba(255,107,53,0.15);
  border: 1px solid rgba(255,107,53,0.3);
  border-radius: 10px;
  color: #FF6B35;
  cursor: pointer;
  font-size: 15px;
  font-weight: 600;
}
/* 抽屉里两个登出按钮之间也加大间距 */
.drawer-logout + .drawer-logout {
  margin-top: 12px;
}

/* ============= 家庭选择器弹窗 ============= */
.picker-body { padding: 4px 2px; }
.picker-search { margin-bottom: 12px; }
.picker-list {
  max-height: 420px;
  overflow-y: auto;
}
.picker-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-radius: 10px;
  cursor: pointer;
  border: 1px solid transparent;
  margin-bottom: 4px;
  transition: all 0.15s;
}
.picker-item:hover {
  background: #f5efe8;
}
.picker-item.active {
  background: #fff3e0;
  border-color: #ffd8a8;
}
.pi-emoji { font-size: 18px; }
.pi-name {
  flex: 1;
  font-weight: 600;
  color: #3d2314;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pi-code {
  background: #fff3e0;
  color: #e67e22;
  padding: 1px 8px;
  border-radius: 6px;
  font-size: 12px;
}
.pi-meta { font-size: 12px; color: #8b6f5c; min-width: 36px; text-align: right; }
.pi-check { color: #FF6B35; font-weight: 700; }
.picker-empty {
  text-align: center;
  color: #c4a882;
  padding: 40px 0;
}
</style>