/**
 * SSR 渲染冒烟：用 Vite 的 SSR 模块加载能力，在 Node 中真实渲染每个路由，
 * 验证所有组件「setup + 模板」无运行时错误（不依赖真实浏览器）。
 * 运行：node scripts/ssr-smoke.mjs（在 eshop-web 目录下）
 */
import { createServer } from 'vite'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'

// 浏览器 API 桩（auth store 初始化读 localStorage）
globalThis.localStorage = { getItem: () => null, setItem() {}, removeItem() {} }
globalThis.window = { addEventListener() {}, dispatchEvent() {}, history: { length: 1 } }

// SSR 下页面里的异步请求（相对路径 /api）无法发出，忽略其未处理拒绝即可（浏览器中由代理承接）
process.on('unhandledRejection', () => {})

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' })
let failed = false

try {
  const view = async (path) => (await server.ssrLoadModule(`/src/views/${path}`)).default

  const cases = [
    { path: '/', file: 'HomeView.vue', expect: ['E-Shop', '热卖推荐'] },
    { path: '/list', file: 'ListView.vue', expect: ['全部商品', '排序'] },
    { path: '/detail/1', file: 'DetailView.vue', expect: ['加载中'] },
    { path: '/cart', file: 'CartView.vue', expect: ['购物车'] },
    { path: '/checkout', file: 'CheckoutView.vue', expect: ['确认订单'] },
    { path: '/cashier/ESX', file: 'CashierView.vue', expect: ['收银台'] },
    { path: '/orders', file: 'OrdersView.vue', expect: ['我的订单'] },
    { path: '/orders/ESX', file: 'OrderDetailView.vue', expect: ['订单详情'] }
  ]

  const App = (await server.ssrLoadModule('/src/App.vue')).default

  for (const c of cases) {
    try {
      const app = createSSRApp(App)
      const router = createRouter({
        history: createMemoryHistory(),
        routes: [{ path: '/:pathMatch(.*)*', component: await view(c.file) }]
      })
      app.use(createPinia())
      app.use(router)
      await router.push(c.path)
      await router.isReady()
      const html = await renderToString(app)
      const missing = c.expect.filter((t) => !html.includes(t))
      if (missing.length) {
        failed = true
        console.log(`FAIL  ${c.path}  (${c.file})  missing: ${missing.join(', ')}`)
      } else {
        console.log(`OK    ${c.path}  (${c.file})  rendered ${html.length} chars`)
      }
    } catch (e) {
      failed = true
      console.log(`ERROR ${c.path}  (${c.file}): ${e.message}`)
    }
  }
} finally {
  await server.close()
}

console.log(failed ? '>>> SSR SMOKE FAILED' : '>>> SSR SMOKE ALL PASS')
process.exit(failed ? 1 : 0)
