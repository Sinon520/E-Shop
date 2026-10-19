<template>
  <AppHeader />
  <main class="container">
    <router-view />
  </main>
  <AppFooter />
  <LoginModal />
  <ToastHost />
  <ChatWidget />
</template>

<script setup>
import { onMounted } from 'vue'
import AppHeader from './components/AppHeader.vue'
import AppFooter from './components/AppFooter.vue'
import LoginModal from './components/LoginModal.vue'
import ToastHost from './components/ToastHost.vue'
import ChatWidget from './components/ChatWidget.vue'
import { useAuthStore } from './stores/auth'
import { useCartStore } from './stores/cart'

const auth = useAuthStore()
const cart = useCartStore()

onMounted(() => {
  // 已登录直接拉取购物车（角标等）
  if (auth.isLoggedIn) {
    cart.fetch().catch(() => {})
  }
})
</script>
