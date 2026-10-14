import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * E-Shop C 端开发配置
 * 开发态经 Vite 代理把 /api 转发到网关（9000），前端同源、无跨域问题
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:9000',
        changeOrigin: true
      }
    }
  }
})
