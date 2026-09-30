<template>
  <div class="family-bar">
    <span class="fb-label">🏠</span>
    <el-select
      :model-value="currentFamilyId"
      placeholder="选择家庭"
      size="default"
      style="width: 180px"
      @change="onSelectChange"
    >
      <el-option
        v-for="f in families"
        :key="f.familyId"
        :label="f.name + ' (' + (f.memberCount || 0) + '人)'"
        :value="f.familyId"
      />
    </el-select>
    <span class="fb-count" v-if="count !== undefined">共 {{ count }} 条</span>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getAdminFamilies } from '../api'
import { getSecureItem, setSecureItem, primeTokenCache } from '../utils/secureStore'

const props = defineProps({
  count: { type: Number, default: undefined }
})

const emit = defineEmits(['family-changed'])

const families = ref([])
const currentFamilyId = ref(null)

// 标记是否由用户手动选择触发的更新（避免重复触发）
let isUserSelect = false

const loadFamilies = async () => {
  try {
    const list = await getAdminFamilies() || []
    families.value = list
  } catch (e) {
    families.value = []
  }
  // 从加密存储恢复当前选中值
  const saved = await getSecureItem('admin_family_id')
  if (saved) {
    const num = Number(saved)
    const found = families.value.find(f => f.familyId === num)
    currentFamilyId.value = found ? found.familyId : (families.value[0] ? families.value[0].familyId : null)
  } else {
    currentFamilyId.value = families.value[0] ? families.value[0].familyId : null
  }
}

const onSelectChange = async (val) => {
  isUserSelect = true
  await setSecureItem('admin_family_id', String(val))
  primeTokenCache('admin_family_id')
  currentFamilyId.value = val
  emit('family-changed', val)
  // 重置标记
  setTimeout(() => { isUserSelect = false }, 100)
}

// 监听 sidebar（App.vue）切换家庭的事件
const onSidebarFamilyChange = (e) => {
  const newFamilyId = Number(e.detail)
  // 更新下拉选中值（从 sidebar 来的，不需要触发 reload）
  currentFamilyId.value = newFamilyId
  // 同步更新 families 列表
  loadFamilies()
}

const onFamiliesChanged = () => loadFamilies()

onMounted(() => {
  loadFamilies()
  window.addEventListener('families-changed', onFamiliesChanged)
  window.addEventListener('admin-family-changed', onSidebarFamilyChange)
})

onUnmounted(() => {
  window.removeEventListener('families-changed', onFamiliesChanged)
  window.removeEventListener('admin-family-changed', onSidebarFamilyChange)
})
</script>

<style scoped>
.family-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  background: #fff8f0;
  border-radius: 10px;
  margin-bottom: 14px;
  border: 1px solid #f0e6dc;
}
.fb-label { font-size: 16px; }
.fb-count { font-size: 13px; color: #8b6f5c; margin-left: 4px; }
</style>
