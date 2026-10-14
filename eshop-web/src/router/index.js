import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'

const routes = [
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue') },
  { path: '/list', name: 'list', component: () => import('../views/ListView.vue') },
  { path: '/detail/:id', name: 'detail', component: () => import('../views/DetailView.vue') },
  { path: '/cart', name: 'cart', component: () => import('../views/CartView.vue'), meta: { auth: true } },
  { path: '/checkout', name: 'checkout', component: () => import('../views/CheckoutView.vue'), meta: { auth: true } },
  { path: '/cashier/:orderNo', name: 'cashier', component: () => import('../views/CashierView.vue'), meta: { auth: true } },
  { path: '/orders', name: 'orders', component: () => import('../views/OrdersView.vue'), meta: { auth: true } },
  { path: '/orders/:orderNo', name: 'orderDetail', component: () => import('../views/OrderDetailView.vue'), meta: { auth: true } },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

/** 需登录页面：未登录先弹登录框，取消则回首页 */
router.beforeEach(async (to) => {
  if (to.meta.auth) {
    const auth = useAuthStore()
    if (auth.isLoggedIn) return true
    const ok = await useUiStore().requireLogin()
    return ok ? true : { name: 'home' }
  }
  return true
})

export default router
