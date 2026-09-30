import { createRouter, createWebHistory } from 'vue-router'
import { cachedToken, primeTokenCache } from './utils/secureStore'

const routes = [
  { path: '/login', name: 'Login', component: () => import('./views/Login.vue') },
  { path: '/', redirect: '/orders' },
  { path: '/orders', name: 'Orders', component: () => import('./views/Orders.vue'), meta: { requiresAuth: true } },
  { path: '/dishes', name: 'Dishes', component: () => import('./views/Dishes.vue'), meta: { requiresAuth: true } },
  { path: '/public-menu', name: 'PublicMenu', component: () => import('./views/PublicMenu.vue'), meta: { requiresAuth: true } },
  { path: '/ingredients', name: 'Ingredients', component: () => import('./views/Ingredients.vue'), meta: { requiresAuth: true } },
  { path: '/public-ingredients', name: 'PublicIngredients', component: () => import('./views/PublicIngredients.vue'), meta: { requiresAuth: true } },
  { path: '/families', name: 'Families', component: () => import('./views/Families.vue'), meta: { requiresAuth: true } },
  { path: '/push-logs', name: 'PushLogs', component: () => import('./views/PushLogs.vue'), meta: { requiresAuth: true } },
  { path: '/reload', name: 'Reload', component: { render: () => null }, meta: { requiresAuth: true } },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach(async (to, from, next) => {
  // 同步缓存可能还没 prime（路由切换在 App.vue 启动前发生），先异步取一次
  let token = cachedToken('chef_token')
  if (!token) {
    token = await primeTokenCache('chef_token')
  }
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router