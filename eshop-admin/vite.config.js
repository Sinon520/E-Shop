import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * E-Shop 管理后台开发配置
 * - /api      → 网关 9000（业务接口，含 /api/admin/**）
 * - /internal → stock 服务 8083 直连（缓存预热等运维操作；网关对 /internal 一律 404）
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    host: '127.0.0.1',
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:9000',
        changeOrigin: true
      },
      '/internal': {
        target: 'http://127.0.0.1:8083',
        changeOrigin: true
      }
    }
  }
})
