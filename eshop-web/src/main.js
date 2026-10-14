import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { useCartStore } from './stores/cart'
import { useUiStore } from './stores/ui'
import { toast } from './utils/toast'
import './styles/base.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)

/** 鉴权失效统一处理（由 request 拦截器广播）：清登录态 + 清本地购物车 + 提示重新登录 */
window.addEventListener('eshop:unauthorized', () => {
  const auth = useAuthStore()
  const cart = useCartStore()
  if (auth.isLoggedIn) {
    auth.clear()
    cart.clearLocal()
    toast('登录已过期，请重新登录')
    useUiStore().openLogin()
  }
})

app.mount('#app')
