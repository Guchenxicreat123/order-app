/**
 * useOnShowDedupe —— 防 onShow 重复请求
 *
 * onShow 在每次页面显示时触发：从其他页面返回会再触发、tabBar 切换会再触发。
 * 直接 `onShow(loadData)` 会导致同一请求瞬间发 2 次、3 次。
 *
 * 用法：
 *   const loadData = useOnShowDedupe(async () => {
 *     const list = await getOrders()
 *     orders.value = list
 *   })
 *   onShow(loadData)
 *
 * 行为：
 *   - 同一函数在请求未完成前再次触发 → 直接复用同一 promise，不发新请求
 *   - 上一次成功/失败后 → 允许下一次调用正常发请求
 *   - 加 200ms 冷却：成功后立即又触发（极少见，但 onShow 偶发）也算重复请求
 */
import { ref, onUnmounted } from 'vue'

export function useOnShowDedupe(fn, options = {}) {
  const cooldownMs = options.cooldownMs ?? 200
  let inflight = null
  const loading = ref(false)
  let lastDoneAt = 0

  const wrapped = (...args) => {
    if (inflight) return inflight
    const now = Date.now()
    if (now - lastDoneAt < cooldownMs) {
      // 冷却期内不重新请求
      return Promise.resolve()
    }
    loading.value = true
    inflight = (async () => {
      try {
        return await fn(...args)
      } finally {
        loading.value = false
        inflight = null
        lastDoneAt = Date.now()
      }
    })()
    return inflight
  }

  onUnmounted(() => { inflight = null })
  return { run: wrapped, loading }
}