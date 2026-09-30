import axios from 'axios'
import { ElMessage } from 'element-plus'
import {
  setSecureItem, removeSecureItem,
  primeTokenCache, cachedToken, clearTokenCache
} from '../utils/secureStore'

// ============ API 地址（从环境变量读取；.env.production / .env.development 配置）============
// VITE_API_URL 示例：http://192.168.x.x:8006/api  或  https://your-domain.example.com/api
const API_BASE = import.meta.env.VITE_API_URL || 'http://192.168.x.x:8006/api'

const service = axios.create({
  baseURL: API_BASE,
  timeout: 10000,
  withCredentials: false  // 当前端到端使用 Bearer token 鉴权，不走 cookie
})

// ============ 启动时异步注入加密 token 到缓存（拦截器同步读取）============
primeTokenCache('chef_token')

service.interceptors.request.use(config => {
  // 从缓存读（已在 App.vue 启动时 prime）
  const token = cachedToken('chef_token')
  if (token) config.headers.Authorization = `Bearer ${token}`

  // 家庭 ID：FamilySelect.vue / App.vue 在 setItem 时同步 prime
  const famId = cachedToken('admin_family_id')
  if (famId) config.headers['X-Family-Id'] = famId

  return config
})

service.interceptors.response.use(
  res => res.data?.code === 0 ? res.data.data : Promise.reject(res.data),
  err => {
    if (err.response?.status === 401 || err.response?.data?.code === 401) {
      removeSecureItem('chef_token')
      clearTokenCache('chef_token')
      if (location.pathname !== '/login') {
        location.href = '/login'
      }
    }
    ElMessage.error(err.response?.data?.message || '网络异常')
    return Promise.reject(err)
  }
)

// ============ 登录 ============
// 只校验管理员账号密码（后端 t_admin_user 表），不再有主厨/角色/家庭等限制。
// 登录成功后自动写入加密存储 + 内存缓存
export const chefLogin = async (username, password) => {
  const res = await service.post('/admin/login', { username, password })
  // res 已是 data 部分：{ token, username, displayName }
  if (res && res.token) {
    await setSecureItem('chef_token', res.token)
    primeTokenCache('chef_token')
    const shown = res.displayName || res.username
    if (shown) await setSecureItem('chef_nickname', shown)
    // 通知 App.vue：登录已完成，重新加载家庭列表（onMounted 在登录前已跑过且无 token）
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('families-changed'))
    }
  }
  return res
}

/** 修改当前管理员的登录密码（后台自助改密） */
export const changeAdminPassword = async (oldPassword, newPassword) => {
  return service.post('/admin/change-password', { oldPassword, newPassword })
}

/**
 * 退出登录：清掉本地加密存储与内存缓存。
 * （新管理服务用无状态 JWT，服务端没有 token_version 需要递增）
 */
export const chefLogout = async () => {
  try { await service.post('/admin/logout') } catch (e) { /* 即使失败也继续清前端 */ }
  removeSecureItem('chef_token')
  removeSecureItem('chef_nickname')
  removeSecureItem('admin_family_id')
  clearTokenCache()
}

// ============ 今日菜单 ============
export const getChefStatus = () => service.get('/me/chef-status')
export const getTodayMenu = () => service.get('/menu/today')
export const getTodayMenuByUser = () => service.get('/menu/today/by-user')
export const getTodayTotal = () => service.get('/menu/today/total')
export const confirmMenuItem = (id) => service.put(`/menu/items/${id}/confirm`)
export const cancelMenuItem = (id) => service.delete(`/menu/items/${id}`)

// ============ 配菜分类 ============
export const getIngredientCategories = () => service.get('/ingredient-categories')
export const createIngredientCategory = (data) => service.post('/ingredient-categories', data)
export const updateIngredientCategory = (id, data) => service.put(`/ingredient-categories/${id}`, data)
export const deleteIngredientCategory = (id) => service.delete(`/ingredient-categories/${id}`)

// ============ 配菜 ============
export const getIngredients = (params) => service.get('/ingredients', { params })
export const getIngredient = (id) => service.get(`/ingredients/${id}`)
export const getIngredientsPublic = () => service.get('/ingredients/public')
export const createIngredient = (data) => service.post('/ingredients', data)
export const updateIngredient = (id, data) => service.put(`/ingredients/${id}`, data)
export const toggleIngredient = (id) => service.put(`/ingredients/${id}/toggle`)
export const deleteIngredient = (id) => service.delete(`/ingredients/${id}`)

// ============ 菜品分类 ============
export const getCategories = () => service.get('/categories')

// ============ 菜谱 ============
export const getDishes = () => service.get('/dishes')
export const getDishesManageAll = () => service.get('/dishes/manage/all')
export const getDishDetail = (id) => service.get(`/dishes/${id}`)
export const getDishRecommendation = (ingIds, threshold) =>
  service.get('/dishes/recommend', { params: { ingIds, threshold } })
export const createDish = (data) => service.post('/dishes', data)
export const updateDish = (id, data) => service.put(`/dishes/${id}`, data)
export const toggleDishStatus = (id) => service.put(`/dishes/${id}/status`)

// ============ 买菜清单 ============
export const getShoppingList = (date) => service.get('/menu/shopping-list', { params: { date } })

// ============ 历史 ============
export const getMenuHistory = (date) => service.get('/menu/today/by-user', { params: { date } })

// ============ 订单 ============
export const getOrders = () => service.get('/orders')
export const getTodayOrders = () => service.get('/orders/today')
export const getOrderDetail = (id) => service.get(`/orders/${id}`)
export const confirmOrder = (id) => service.post(`/orders/${id}/confirm`)
export const confirmOrderItem = (id) => service.post(`/orders/items/${id}/confirm`)
export const rejectOrderItem = (id) => service.post(`/orders/items/${id}/reject`)
export const updateOrder = (id, data) => service.put(`/orders/${id}`, data)
export const deleteOrder = (id) => service.delete(`/orders/${id}`)

// ============ 公共菜单 ============
export const getPublicDishes = () => service.get('/public/dishes/admin/all')
export const createPublicDish = (data) => service.post('/public/dishes', data)
export const updatePublicDish = (id, data) => service.put(`/public/dishes/${id}`, data)
export const deletePublicDish = (id) => service.delete(`/public/dishes/${id}`)

// ============ 公共配菜 ============
export const getPublicIngredients = () => service.get('/public/ingredients/admin/all')
export const getPublicIngredientCategories = () => service.get('/public/ingredients/categories')
export const createPublicIngredient = (data) => service.post('/public/ingredients', data)
export const updatePublicIngredient = (id, data) => service.put(`/public/ingredients/${id}`, data)
export const deletePublicIngredient = (id) => service.delete(`/public/ingredients/${id}`)

// ============ 用户管理 ============
export const getAdminUsers = () => service.get('/admin/users')
export const getAdminFamilies = () => service.get('/admin/families')
export const getAdminFamiliesOverview = () => service.get('/admin/families/overview')
export const createFamily = (data) => service.post('/admin/families', data)
export const updateFamily = (familyId, data) => service.put(`/admin/families/${familyId}`, data)
export const deleteFamily = (familyId) => service.delete(`/admin/families/${familyId}`)
export const adminAddFamily = (userId, data) => service.post(`/admin/users/${userId}/add-family`, data)
export const adminRemoveFamily = (userId, data) => service.post(`/admin/users/${userId}/remove-family`, data)
export const adminSetRole = (userId, data) => service.post(`/admin/users/${userId}/set-role`, data)
export const adminDeleteUser = (userId) => service.delete(`/admin/users/${userId}`)
/** 强制下线：撤销指定用户全部已签发 token */
export const adminRevokeUser = (userId) => service.post(`/admin/users/${userId}/revoke`)

// ============ 推送记录（管理后台视图）============
// 不走小程序后端：直读 t_push_log 并关联 t_user / t_family / t_menu_item。
// 推送失败只能看这里 —— docker 里 PUSH_DRY_RUN=true 时所有 row 的 error_msg='dry-run'。
//
// 过滤参数：
//   familyId 数字（缺省＝全部）
//   type     'NEW_ORDER' | 'STATUS_CHANGED' | ''（缺省＝全部）
//   success  1（成功）| 0（失败）| ''（缺省＝全部）
//   limit    默认 100，上限 500
//   offset   默认 0
export const getPushLogs = (params = {}) => service.get('/push/logs', { params })
export const getPushStats = () => service.get('/push/stats')
