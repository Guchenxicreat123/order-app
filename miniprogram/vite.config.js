import { defineConfig, loadEnv } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  // 默认服务器地址
  const serverUrl = env.VITE_API_URL || 'http://192.168.x.x:8006'

  return {
    plugins: [uni()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: serverUrl,
          changeOrigin: true,
          rewrite: (path) => path
        }
      }
    }
  }
})
