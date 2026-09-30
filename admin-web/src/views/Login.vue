<template>
  <div class="login-page">
    <!-- 背景光效 -->
    <div class="orb orb1"></div>
    <div class="orb orb2"></div>
    <div class="orb orb3"></div>

    <div class="login-card">
      <div class="card-header">
        <div class="header-bar"></div>
        <h2>🍳 主厨登录</h2>
        <p class="subtitle">家庭厨房助手 · 主厨后台</p>
      </div>

      <el-form class="login-form" @submit.prevent="handleLogin">
        <el-form-item>
          <el-input
            v-model="form.username"
            placeholder="管理员账号"
            size="large"
            prefix-icon="👤"
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="管理员密码"
            size="large"
            prefix-icon="🔑"
            show-password
          />
        </el-form-item>
        <el-button
          type="primary"
          class="login-btn"
          :loading="loading"
          native-type="submit"
          @click="handleLogin"
        >
          进入厨房
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { chefLogin } from '../api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const form = ref({ username: 'admin', password: '123456' })
const loading = ref(false)

const handleLogin = async () => {
  if (!form.value.username) {
    ElMessage.warning('请输入管理员账号')
    return
  }
  if (!form.value.password) {
    ElMessage.warning('请输入管理员密码')
    return
  }
  loading.value = true
  try {
    // chefLogin 内部会写加密 token + nickname，无需前端再 setItem
    await chefLogin(form.value.username, form.value.password)
    router.push('/orders')
  } catch (e) {
    // 错误已由 axios 拦截器 ElMessage.error 提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  background: #1a0e08;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  padding: 24px;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.35;
  animation: float 6s ease-in-out infinite;
}

.orb1 { width: 400px; height: 400px; background: #FF6B35; top: -100px; left: -100px; animation-delay: 0s; }
.orb2 { width: 300px; height: 300px; background: #FFB347; bottom: -80px; right: -80px; animation-delay: 2s; }
.orb3 { width: 200px; height: 200px; background: #8B4513; top: 50%; left: 50%; animation-delay: 4s; }

@keyframes float {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(20px, -20px); }
}

.login-card {
  background: rgba(40, 20, 10, 0.85);
  backdrop-filter: blur(30px);
  border: 1px solid rgba(255, 107, 53, 0.2);
  border-radius: 20px;
  padding: 0;
  width: 380px;
  max-width: 100%;
  position: relative;
  z-index: 1;
  box-shadow: 0 20px 60px rgba(0,0,0,0.5);
  overflow: hidden;
  animation: fadeUp 0.6s ease;
}

@media (max-width: 767px) {
  .login-card { width: 100%; border-radius: 16px; }
  .card-header h2 { font-size: 20px; }
  .login-btn { height: 48px !important; font-size: 16px !important; }
  .orb { filter: blur(40px); opacity: 0.25; }
}

@keyframes fadeUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.card-header {
  padding: 0;
  text-align: center;
}

.header-bar {
  height: 6px;
  background: linear-gradient(90deg, #FF6B35, #FFB347, #FF6B35);
}

.card-header h2 {
  color: #f8f0e3;
  font-size: 22px;
  margin-top: 24px;
  font-weight: 600;
}

.subtitle {
  color: #8b6f5c;
  font-size: 12px;
  margin-top: 6px;
  margin-bottom: 0;
}

.login-form {
  padding: 24px 32px 32px;
}

.login-btn {
  width: 100%;
  height: 44px;
  background: linear-gradient(135deg, #FF6B35, #FF8E53) !important;
  border: none !important;
  border-radius: 10px !important;
  font-size: 15px;
  font-weight: 600;
  color: white !important;
  letter-spacing: 2px;
  margin-top: 8px;
  transition: transform 0.15s, box-shadow 0.15s;
}

.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(255, 107, 53, 0.4);
}

:deep(.el-input__wrapper) {
  background: rgba(255,255,255,0.06) !important;
  border: 1px solid rgba(255,255,255,0.12) !important;
  box-shadow: none !important;
  border-radius: 10px !important;
  padding: 8px 12px !important;
}

:deep(.el-input__inner) {
  color: #f8f0e3 !important;
  font-size: 14px;
}

:deep(.el-input__inner::placeholder) {
  color: #6b4f3a !important;
}

:deep(.el-input__prefix-icon) {
  color: #8b6f5c !important;
}
</style>
