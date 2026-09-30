// ⚠️ 服务器地址
// 小程序端固定用公网域名；H5 预览走 vite proxy（/api → 内网后端）
// #ifdef H5
const BASE_URL = ''
// #endif
// #ifndef H5
const BASE_URL = 'https://your-domain.example.com'
// #endif

// 存什么 key
const TOKEN_KEY = 'token'
const USER_KEY = 'userInfo'

/** 把 uni.login 包成带超时的 Promise（真机上 wx.login 可能 hang 住不回调，必须有兜底） */
function wxLoginWithTimeout(timeoutMs = 2500) {
  return new Promise((resolve) => {
    let settled = false
    const finish = (val) => { if (!settled) { settled = true; resolve(val) } }
    // #ifdef MP-WEIXIN
    try {
      uni.login({
        provider: 'weixin',
        success: (r) => finish({ ok: true, res: r }),
        fail: (e) => finish({ ok: false, err: e })
      })
    } catch (e) { finish({ ok: false, err: e }) }
    // #endif
    // #ifndef MP-WEIXIN
    finish({ ok: false, err: 'no-mp-weixin' })
    // #endif
    setTimeout(() => finish({ ok: false, err: 'timeout', timeout: true }), timeoutMs)
  })
}

/** 单次尝试登录（不重试）。返回 boolean，是否成功拿到 token */
async function tryLoginOnce() {
  try {
    let payload = uni.getStorageSync('deviceCode')
    if (!payload) {
      payload = 'dev_' + Date.now() + '_' + Math.floor(Math.random() * 10000)
      uni.setStorageSync('deviceCode', payload)
    }
    // #ifdef MP-WEIXIN
    try {
      const r = await wxLoginWithTimeout(2500)
      if (r && r.ok && r.res && r.res.code) payload = r.res.code
      // 真机上 wx.login 超时/失败时，payload 仍保留 deviceCode（mock 模式会直接用 deviceCode 当 openid）
    } catch (e) {}
    // #endif
    const data = await wxLogin(payload)
    if (data && data.token) {
      uni.setStorageSync(TOKEN_KEY, data.token)
      uni.setStorageSync(USER_KEY, data)
      return true
    }
  } catch (e) {
    console.warn('tryLoginOnce failed', e)
  }
  return false
}

/** 自动重新登录：使用真实 wx.login code 或 deviceCode，失败时清空 storage */
async function tryRelogin() {
  return await tryLoginOnce()
}

/** 持续重试登录：失败则一直重试（指数退避，封顶 12s），成功立即返回 true。
 *  可通过返回的 cancel() 主动停止。 */
export function forceLogin({ showLoading = false } = {}) {
  let cancelled = false
  const cancel = () => { cancelled = true }
  // 正在重试的协程仅一份（避免多次点击造成并发）
  if (forceLogin._p) return { promise: forceLogin._p, cancel }
  const p = (async () => {
    if (showLoading) {
      try { uni.showLoading({ title: '登录中…', mask: true }) } catch (e) {}
    }
    // 第一次直接尝试
    if (await tryLoginOnce()) { finish(true); return true }
    // 退避序列：1.5, 3, 6, 12, 12... (封顶 12s)
    const delays = [1500, 3000, 6000, 12000, 12000, 12000, 12000, 12000, 12000, 12000, 12000, 12000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000]
    for (const d of delays) {
      if (cancelled) break
      await new Promise(r => setTimeout(r, d))
      if (cancelled) break
      if (await tryLoginOnce()) { finish(true); return true }
    }
    finish(false)
    return false
  })()
  function finish(ok) {
    if (showLoading) { try { uni.hideLoading() } catch (e) {} }
    forceLogin._p = null
    if (ok) {
      // 登录成功 → 清除「用户主动退出」标记（401 时恢复静默重登）
      try { uni.removeStorageSync('logout_by_user') } catch (e) {}
      // 异步通知整个 app 登录状态变化，避免 emit 时机问题
      // （emit 是同步广播，如果 emit 时页面正在切换 / onUnloading 阶段，
      //   部分 listener 已经被 off，可能触发 uni.$emit 内部的 addListener 错误）
      setTimeout(() => {
        try { uni.$emit && uni.$emit('auth:login') } catch (e) {}
      }, 0)
    }
  }
  forceLogin._p = p
  return { promise: p, cancel }
}

/** 监听登录状态变化（forceLogin 成功后触发） */
export function onAuthLogin(handler) {
  if (typeof uni === 'undefined' || !uni.$on) return () => {}
  uni.$on('auth:login', handler)
  return () => { try { uni.$off && uni.$off('auth:login', handler) } catch (e) {} }
}

/** 等待登录完成：若正在登录中，等它结束再返回；否则立即返回。
 *  用于避免「登录请求还在飞，ui 却已经按未登录状态渲染」的竞态。 */
export function whenLoggedIn() {
  return forceLogin._p || Promise.resolve(true)
}

/** 当前是否已登录（本地有 token 且未过期） */
export function isLoggedIn() {
  const t = uni.getStorageSync(TOKEN_KEY)
  if (!t) return false
  try {
    if (typeof isTokenExpired === 'function' && isTokenExpired(t)) return false
  } catch (e) {}
  return true
}

// ========== 基础请求 ==========
// 发送一次 uni.request；返回 Promise<{ok:true, payload} | {ok:false, code, message, network:true}>
function requestOnce({ url, method, data, header }) {
  return new Promise((resolve) => {
    uni.request({
      url: BASE_URL + url,
      method,
      data,
      header,
      timeout: 15000,
      success: (res) => resolve({ ok: true, res }),
      fail: (err) => resolve({ ok: false, network: true, err })
    })
  })
}

function request({ url, method = 'GET', data = {}, quiet = false, _retried = false }) {
  const token = uni.getStorageSync(TOKEN_KEY)
  const header = {
    Authorization: token ? `Bearer ${token}` : '',
    'Content-Type': 'application/json'
  }
  return new Promise(async (resolve, reject) => {
    let r = await requestOnce({ url, method, data, header })
    // 网络层失败：最多重试 3 次（指数退避），覆盖真机首次访问公网 HTTPS 的
    // ERR_CONNECTION_CLOSED / TLS 握手被服务端关闭 / CDN 冷启动等偶发错误
    if (!r.ok && r.network) {
      const delays = [400, 800, 1600]
      let attempt = 0
      while (attempt < delays.length) {
        await new Promise(res => setTimeout(res, delays[attempt]))
        r = await requestOnce({ url, method, data, header })
        if (r.ok) { r.retried = true; r.retryCount = attempt + 1; break }
        attempt += 1
      }
    }
    // 网关/服务暂时不可用（502/503/504/500）也重试一次，规避冷启动/C瞬回源
    if (r.ok && !_retried && [500, 502, 503, 504].includes(r.res.statusCode)) {
      await new Promise(r => setTimeout(r, 600))
      const r2 = await requestOnce({ url, method, data, header })
      if (r2.ok) { r = r2; r.retried = true }
    }
    if (!r.ok) {
      // 网络失败（重试后仍然失败）
      const isTimeout = r.err && /timeout/i.test(r.err.errMsg || '')
      const msg = isTimeout ? '网络超时，请稍后再试' : '网络异常，请检查网络'
      if (!quiet) uni.showToast({ title: msg, icon: 'none' })
      return reject({ network: true, message: msg, err: r.err })
    }
    const res = r.res
    if (res.statusCode === 200 && res.data?.code === 0) {
      resolve(res.data.data)
    } else if (res.statusCode === 401 || res.data?.code === 401) {
      // 401：清 token + 尝试自动重新登录（仅一次）
      // 注意：只有「本来带着 token 却被拒绝」（= 会话过期）才自动重登。
      // 从来没有 token（首次打开 / 已退出登录）时绝不自动登录 —— 否则用户还没点
      // 「立即登录」，后台就自己把用户登进去了。这种情况直接把 401 交给上层，
      // 由页面引导用户手动点登录。
      const hadToken = (() => {
        try { return !!uni.getStorageSync(TOKEN_KEY) } catch (e) { return false }
      })()
      uni.removeStorageSync(TOKEN_KEY)
      uni.removeStorageSync(USER_KEY)
      // ⚠️ 用户主动退出登录后（logout_by_user flag 置位），不自动重登录 ——
      // 业务请求直接 401 失败，让上层走手动登录路径（如欢迎页按钮 / 主页入口的 guardLogin）
      const isLogoutByUser = (() => {
        try { return !!uni.getStorageSync('logout_by_user') } catch (e) { return false }
      })()
      if (isLogoutByUser || !hadToken) {
        if (!quiet && hadToken) uni.showToast({ title: '请先登录', icon: 'none' })
        return reject(res.data)
      }
      if (!quiet) uni.showToast({ title: '登录已过期，正在重新登录…', icon: 'none' })
      const ok = await tryRelogin()
      if (ok) {
        // 重登成功：自动重试原请求一次
        const newToken = uni.getStorageSync(TOKEN_KEY)
        const r2 = await requestOnce({
          url, method, data,
          header: { Authorization: `Bearer ${newToken}`, 'Content-Type': 'application/json' }
        })
        if (r2.ok && r2.res.statusCode === 200 && r2.res.data?.code === 0) {
          return resolve(r2.res.data.data)
        }
      }
      if (!quiet) uni.showToast({ title: '请重新进入小程序', icon: 'none' })
      return reject(res.data)
    } else {
      // 这里包括 500/502/503/504（重试后还失败），以及其它业务错误码
      const code = res.statusCode
      const msg = res.data?.message || (code === 502 ? '服务暂时不可用，请稍后再试' : (code === 503 ? '服务维护中' : '请求失败'))
      if (!quiet) uni.showToast({ title: msg, icon: 'none' })
      reject(res.data)
    }
  })
}

// ========== 认证 ==========
/**
 * 微信登录。code 为 wx.login 临时凭证（真实环境）或本地标识（开发 mock）。
 * 同时自动附带持久 deviceCode：后端在 secret 未配置（mock 模式）时
 * 用它拼 mock_<deviceCode> 作为 openid，保证同一设备稳定复用同一账号。
 */
/** 取本机持久 deviceCode（开发模式的身份兜底） */
function ensureDeviceCode() {
  let deviceCode = uni.getStorageSync('deviceCode')
  if (!deviceCode) {
    deviceCode = 'dev_' + Date.now() + '_' + Math.floor(Math.random() * 10000)
    uni.setStorageSync('deviceCode', deviceCode)
  }
  return deviceCode
}

/** 取一次微信登录 code（真机失败/超时时退化为 deviceCode） */
async function obtainLoginCode() {
  const deviceCode = ensureDeviceCode()
  let code = deviceCode
  // #ifdef MP-WEIXIN
  try {
    const r = await wxLoginWithTimeout(2500)
    if (r && r.ok && r.res && r.res.code) code = r.res.code
  } catch (e) {}
  // #endif
  return { code, deviceCode }
}

export const wxLogin = (code) => {
  return request({ url: '/api/wx/login', method: 'POST', data: { code, deviceCode: ensureDeviceCode() }, quiet: true })
}

/**
 * 只问后端「这个用户是否已经在库里」，不会写入任何数据。
 * 小程序刚打开时用它决定：老用户直接进 / 新用户等点登录。
 * 返回 { exists: boolean }；网络/微信 code 异常时抛错（上层按"未确认"处理）。
 */
export async function checkUserExists() {
  const { code, deviceCode } = await obtainLoginCode()
  const data = await request({
    url: '/api/wx/exists', method: 'POST', data: { code, deviceCode }, quiet: true,
  })
  return data || { exists: false }
}

export const getChefStatus = () => request({ url: '/api/me/chef-status', quiet: true })

// ========== 分类 & 菜品 ==========
export const getCategories = () => request({ url: '/api/categories', quiet: true })

export const getDishes = () => request({ url: '/api/dishes', quiet: true })

export const getDishDetail = (id) => request({ url: `/api/dishes/${id}`, quiet: true })

export const updateDish = (id, data) => request({ url: `/api/dishes/${id}`, method: 'PUT', data })
export const deleteDish = (id) => request({ url: `/api/dishes/${id}`, method: 'DELETE' })
export const batchSaveDishes = (data) => request({ url: '/api/dishes/batch-save', method: 'POST', data })
export const getFamilyDishesAll = () => request({ url: '/api/dishes/all-family', quiet: true })

export const getRecommend = (ingIds, threshold = 0.8) =>
  request({ url: '/api/dishes/recommend', data: { ingIds: ingIds.join(','), threshold }, quiet: true })

// ========== 配菜 ==========
export const getIngredients = () => request({ url: '/api/ingredients/public', quiet: true })
export const getIngredientCategories = () => request({ url: '/api/ingredient-categories', quiet: true })
export const batchSaveIngredients = (data) => request({ url: '/api/ingredients/batch-save', method: 'POST', data })

// ========== 今日菜单 ==========
export const getTodayMenu = () => request({ url: '/api/menu/today', quiet: true })

export const getTodayTotal = () => request({ url: '/api/menu/today/total', quiet: true })

// 买菜清单（聚合今日已确认菜品的配菜用量）
export const getShoppingList = (date) => request({
  url: '/api/menu/shopping-list' + (date ? `?date=${date}` : ''),
  quiet: true
})

// ========== 家庭 ==========
export const getMyFamilies = () => request({ url: '/api/family/my', quiet: true })
  .catch(() => [])

export const createFamily = (name) =>
  request({ url: '/api/family/create', method: 'POST', data: { name } })

export const joinFamily = (code) =>
  request({ url: '/api/family/join', method: 'POST', data: { code } })

export const switchFamily = (familyId) =>
  request({ url: '/api/family/switch', method: 'POST', data: { familyId } })

export const leaveFamily = (familyId) =>
  request({ url: `/api/family/${familyId}/leave`, method: 'POST' })

export const getFamilyMembers = (familyId) =>
  request({ url: `/api/family/${familyId}/members`, quiet: true })

export const claimChef = (familyId) =>
  request({ url: `/api/family/${familyId}/claim-chef`, method: 'POST' })

export const resignChef = (familyId) =>
  request({ url: `/api/family/${familyId}/resign-chef`, method: 'POST' })

export const kickMember = (familyId, userId) =>
  request({ url: `/api/family/${familyId}/kick`, method: 'POST', data: { userId } })

export const getFamilyChefStatus = (familyId) =>
  request({ url: `/api/family/${familyId}/chef-status`, quiet: true })

// ========== 菜单操作 ==========
export const addMenuItem = (data) => request({ url: '/api/menu/items', method: 'POST', data })

export const confirmMenuItem = (id) => request({ url: `/api/menu/items/${id}/confirm`, method: 'PUT' })

export const cancelMenuItem = (id) => request({ url: `/api/menu/items/${id}`, method: 'DELETE' })

// ========== 公共菜单 ==========
export const getPublicDishes = () => request({ url: '/api/public/dishes', quiet: true })

/** 公共菜品列表（含每道菜的配方）—— 用于启动时一次性预加载到本地缓存 */
export const getPublicDishesWithIngredients = () =>
  request({ url: '/api/public/dishes/with-ingredients', quiet: true })

/** 公共菜品详情（含配方） */
export const getPublicDishDetail = (id) =>
  request({ url: `/api/public/dishes/${id}`, quiet: true })

export const addPublicDishesToFamily = (publicDishIds) => {
  // 从用户信息中获取当前活跃家庭 ID
  const userInfo = uni.getStorageSync(USER_KEY) || {}
  const families = userInfo.families || []
  const active = families.find(f => f.active) || families[0]
  const familyId = active ? active.familyId : null
  return request({ url: '/api/family/dishes/from-public', method: 'POST', data: { familyId, publicDishIds } })
}

// ========== 公共配菜 ==========
export const getPublicIngredients = () => request({ url: '/api/public/ingredients', quiet: true })
export const getPublicIngredientCategories = () => request({ url: '/api/public/ingredients/categories', quiet: true })

export const addPublicIngredientsToFamily = (publicIngredientIds) => {
  // 从用户信息中获取当前活跃家庭 ID
  const userInfo = uni.getStorageSync(USER_KEY) || {}
  const families = userInfo.families || []
  const active = families.find(f => f.active) || families[0]
  const familyId = active ? active.familyId : null
  return request({ url: '/api/public/ingredients/to-family', method: 'POST', data: { familyId, publicIngredientIds } })
}

// ========== 购物车 ==========
export const addToCart = (data) => request({ url: '/api/cart/add', method: 'POST', data })

export const getMyCart = () => request({ url: '/api/cart', quiet: true })
  .catch(() => [])

export const removeCartItem = (id) => request({ url: `/api/cart/${id}`, method: 'DELETE' })
export const updateCartQuantity = (id, quantity) => request({ url: `/api/cart/${id}`, method: 'PUT', data: { quantity } })

export const checkoutCart = (cartIds) =>
  request({ url: '/api/cart/checkout', method: 'POST', data: { cartIds } })

// ========== 订单 ==========
export const checkoutOrders = (cartIds, remark) =>
  request({ url: '/api/orders/checkout', method: 'POST', data: { cartIds, remark } })

export const getMyOrders = () =>
  request({ url: '/api/orders', quiet: true }).catch(() => [])

export const getTodayOrders = () =>
  request({ url: '/api/orders/today', quiet: true }).catch(() => [])

export const getOrderDetail = (orderId) =>
  request({ url: `/api/orders/${orderId}`, quiet: true }).catch(() => null)

export const confirmOrder = (orderId) =>
  request({ url: `/api/orders/${orderId}/confirm`, method: 'POST' })

export const confirmOrderItem = (itemId) =>
  request({ url: `/api/orders/items/${itemId}/confirm`, method: 'POST' })

export const rejectOrderItem = (itemId) =>
  request({ url: `/api/orders/items/${itemId}/reject`, method: 'POST' })

// ========== 推送 ==========
export const bindPushAuth = (allow) => request({ url: '/api/push/auth', method: 'POST', data: { allow } })

/** 取推送配置（模板 ID、当前剩余可发条数） */
export const getPushConfig = (role) =>
  request({ url: `/api/push/config?role=${role || ''}`, quiet: true }).catch(() => null)

/** 上报一次订阅授权成功（微信一次性订阅：一次允许 = 一条推送） */
export const reportSubscribeGrant = (templateId, count = 1, role = '') =>
  request({ url: '/api/push/subscribe-grant', method: 'POST', data: { templateId, count, role }, quiet: true })
    .catch(() => null)

/** 主厨自检：给自己发一条测试推送 */
export const testPush = () => request({ url: '/api/push/test', method: 'POST' })

/** 推送概况 + 最近记录 */
export const getPushLog = (limit = 20) =>
  request({ url: `/api/push/log?limit=${limit}`, quiet: true }).catch(() => null)

// ========== Token / UserInfo 管理 ==========
export const getToken = () => uni.getStorageSync(TOKEN_KEY)
export const setToken = (t) => uni.setStorageSync(TOKEN_KEY, t)
export const getUserInfo = () => uni.getStorageSync(USER_KEY) || {}
export const setUserInfo = (u) => uni.setStorageSync(USER_KEY, u)

/** 判断本地 JWT 是否已过期（解析 payload.exp） */
export function isTokenExpired(token) {
  if (!token || typeof token !== 'string') return true
  const parts = token.split('.')
  if (parts.length !== 3) return true
  try {
    // 兼容 base64url
    const json = JSON.parse(decodeURIComponent(escape(atob(
      parts[1].replace(/-/g, '+').replace(/_/g, '/')
    ))))
    if (typeof json.exp !== 'number') return false
    return Date.now() >= json.exp * 1000
  } catch (e) {
    return true // 解析失败视为无效，走重登
  }
}

/** 修改当前用户昵称，成功后端会写 t_user.nickname；同时更新本地缓存 */
export const updateNickname = async (nickname) => {
  const data = await request({ url: '/api/me/nickname', method: 'PUT', data: { nickname } })
  if (data && data.nickname) {
    const info = getUserInfo() || {}
    info.nickname = data.nickname
    setUserInfo(info)
  }
  return data
}

/** 修改当前用户头像 key（avatar-1 ~ avatar-6），同时更新本地缓存 */
export const updateAvatar = async (avatarKey) => {
  const data = await request({ url: '/api/me/avatar', method: 'PUT', data: { avatarKey } })
  if (data && data.avatarKey) {
    const info = getUserInfo() || {}
    info.avatarUrl = data.avatarKey
    setUserInfo(info)
  }
  return data
}

/** 头像 key → 静态资源路径（如 "avatar-3" → "/static/avatars/avatar-3.svg")
 *  null/undefined 默认返回 avatar-1 */
export const avatarSrc = (key) => {
  if (!key || typeof key !== 'string') return '/static/avatars/avatar-1.svg'
  if (/^avatar-[1-6]$/.test(key)) return `/static/avatars/${key}.svg`
  return '/static/avatars/avatar-1.svg'
}

/** 头像 key 列表（前端展示用） */
export const AVATAR_KEYS = ['avatar-1', 'avatar-2', 'avatar-3', 'avatar-4', 'avatar-5', 'avatar-6']
export const getActiveFamilyFromInfo = () => {
  const info = getUserInfo()
  const fams = info.families || []
  const active = fams.find(f => f.active) || fams[0]
  return active ? active.familyId : null
}
export const clearAuth = () => {
  // 完整清理：token + userInfo + activeFamilyId
  // 注意：不清 deviceCode —— 退出登录后下次进入仍视为同一设备，
  // 这样后端可以找回原用户的所有数据（购物车、历史订单、家庭等）。
  // deviceCode 在用户主动「重置账号」或清缓存时才清掉。
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
  try { uni.removeStorageSync('activeFamilyId') } catch (e) {}
}

/** 清除「用户主动退出」标记 —— 登录成功后调用，使 401 能再次静默重登。 */
export const clearLogoutByUserFlag = () => {
  try { uni.removeStorageSync('logout_by_user') } catch (e) {}
}
