import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

/**
 * 管理后台路由：/login 独立页；其余页面挂在 AdminLayout 下（侧栏 + 顶栏）
 */
const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { title: '登录' } },
  {
    path: '/',
    component: () => import('../layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '数据看板', crumb: '运营总览' } },
      { path: 'goods', name: 'goods', component: () => import('../views/GoodsView.vue'), meta: { title: '商品管理', crumb: 'SPU / SKU 二级结构' } },
      { path: 'category', name: 'category', component: () => import('../views/CategoryView.vue'), meta: { title: '分类管理', crumb: '一二级分类维护' } },
      { path: 'stock', name: 'stock', component: () => import('../views/StockView.vue'), meta: { title: '库存管理', crumb: '实时账目与 Redis 预扣' } },
      { path: 'orders', name: 'orders', component: () => import('../views/OrdersView.vue'), meta: { title: '订单管理', crumb: '查询 / 发货' } },
      { path: 'users', name: 'users', component: () => import('../views/UsersView.vue'), meta: { title: '用户管理', crumb: '账号与状态' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/** 未登录访问受限页 → 登录页（携带回跳地址） */
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.name !== 'login' && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isLoggedIn) {
    return { name: 'dashboard' }
  }
  document.title = to.meta.title ? `${to.meta.title} · E-Shop 管理后台` : 'E-Shop 管理后台'
  return true
})

export default router
