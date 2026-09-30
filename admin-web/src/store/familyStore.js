/**
 * 简单的响应式家庭状态管理（替代 Vuex/Pinia，V2 重写）
 * App.vue 写入，Views.vue inject 读取
 *
 * 注：admin_family_id 已迁移到 secureStore 加密存储；
 * 这里只保留内存态引用，避免每个页面单独解密。
 */
import { reactive } from 'vue'
import { getSecureItem, primeTokenCache } from './utils/secureStore'

const state = reactive({
  /** 当前选中的 familyId（从加密存储恢复） */
  currentFamilyId: null,
  /** 全部家庭列表 */
  families: [],
  /** 是否正在加载 */
  loading: false,
})

// 异步初始化当前家庭 ID（不阻塞模块加载）
getSecureItem('admin_family_id').then(v => {
  if (v) {
    const num = Number(v)
    if (!Number.isNaN(num)) {
      state.currentFamilyId = num
      primeTokenCache('admin_family_id')
    }
  }
})

export function useFamilyStore() {
  return state
}