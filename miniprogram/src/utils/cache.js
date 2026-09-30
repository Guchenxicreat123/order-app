/**
 * 简易本地缓存工具（基于 uni.storage）
 * - 用法：cachedFetch(key, fetcher, ttlMs)
 * - 命中：直接返回缓存数据（0ms 网络）
 * - 未命中：调 fetcher，把结果写入 storage
 * - 默认 30 分钟过期，调用方传 ttlMs 自定义
 *
 * 适用：
 *   - 公共菜/公共配菜库（变动少）
 *   - 菜品分类、配置等"读多写少"的数据
 *
 * 不适用：
 *   - 订单列表、购物车（必须实时）
 *   - 用户信息（敏感，每次校验）
 *
 * 注意：
 *   - 错误不入缓存（fetcher 抛错时直接冒泡，不污染 storage）
 *   - 静默失败（storage 写入失败不影响业务流）
 */
const DEFAULT_TTL = 30 * 60 * 1000  // 30 分钟

export function cachedFetch(key, fetcher, ttlMs = DEFAULT_TTL) {
  // 1. 读缓存
  let cached = null
  try {
    cached = uni.getStorageSync(key)
  } catch (e) {
    // storage 读失败，照常走 fetcher
  }

  if (cached && cached.ts && (Date.now() - cached.ts) < ttlMs) {
    // 命中
    return Promise.resolve(cached.data)
  }

  // 2. 未命中，调 fetcher
  return Promise.resolve()
    .then(() => fetcher())
    .then((data) => {
      try {
        uni.setStorageSync(key, { ts: Date.now(), data })
      } catch (e) {
        // storage 写失败（容量满？），静默忽略
      }
      return data
    })
}

/**
 * 主动失效某个 key 的缓存
 * 用例：用户在管理后台改了公共菜数据，下次调用 cachedFetch 时强制刷新
 */
export function invalidate(key) {
  try {
    uni.removeStorageSync(key)
  } catch (e) {
    // ignore
  }
}

/**
 * 失效"公共菜/公共配菜"全部相关 key
 * 用例：管理员改了任意公共数据 → 在 App.vue onShow 里调一次
 * 实现：用前缀匹配（storage 是按 key 存的）
 */
export function invalidatePublicLibrary() {
  const prefixes = [
    'public_dish_detail:',
    'public_dishes_all_with_ingredients',
    'public_ingredients_all',
    'public_ingredient_categories',
    'public_dishes_batch:',
  ]
  try {
    const info = uni.getStorageInfoSync()
    for (const k of info.keys) {
      if (prefixes.some(p => k.startsWith(p))) {
        uni.removeStorageSync(k)
      }
    }
  } catch (e) {
    // ignore
  }
}