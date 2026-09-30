import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'
import legacy from '@vitejs/plugin-legacy'

export default defineConfig({
  // ⚠️ 容器直接 expose 8007 端口部署，没有反代层剥离前缀。
  // base 必须是 '/'。如果将来要挂在 `https://domain/admin-web/` 下，
  // 需要加 nginx 反代 location /admin-web/ → :8007，并改回 '/admin-web/'。
  base: '/',
  plugins: [
    vue(),
    legacy({
      targets: ['defaults', 'Android >= 6', 'iOS >= 10', 'Chrome >= 49', 'Safari >= 10.0'],
      additionalLegacyPolyfills: ['regenerator-runtime/runtime']
    }),
    AutoImport({ resolvers: [ElementPlusResolver()] }),
    Components({ resolvers: [ElementPlusResolver()] })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5174,
    proxy: {
      // 开发时把 /api 打到独立的后台管理服务（admin-api），与小程序后端无关
      '/api': {
        target: process.env.ADMIN_API_URL || 'http://localhost:8008',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets'
  }
})
