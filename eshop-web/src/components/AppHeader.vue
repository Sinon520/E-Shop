<template>
  <header class="header">
    <div class="container header-in">
      <a class="logo" @click="router.push('/')"><span class="logo-mark">E</span>E-Shop</a>
      <div class="search">
        <input
          v-model="keyword"
          placeholder="搜索商品，例如：蓝牙耳机"
          @keydown.enter="doSearch"
        />
        <button @click="doSearch">搜索</button>
      </div>
      <nav class="nav">
        <a class="nav-link" :class="{ on: route.name === 'home' }" @click="router.push('/')">首页</a>
        <a class="nav-link" :class="{ on: route.name === 'list' }" @click="router.push('/list')">全部商品</a>
        <a class="cart-btn" @click="goCart">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="21" r="1.6"/><circle cx="19" cy="21" r="1.6"/><path d="M2.5 3h2l2.6 12.4a2 2 0 0 0 2 1.6h9.2a2 2 0 0 0 2-1.6L22 7H6"/></svg>
          购物车
          <span class="cart-badge" v-if="cart.count > 0">{{ cart.count }}</span>
        </a>
        <a class="nav-link" v-if="auth.isLoggedIn" @click="router.push('/orders')">我的订单</a>
        <button class="login-btn" @click="userClick">{{ auth.isLoggedIn ? auth.displayName : '登录' }}</button>
      </nav>
    </div>
  </header>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useCartStore } from '../stores/cart'
import { useUiStore } from '../stores/ui'
import { toast } from '../utils/toast'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()
const ui = useUiStore()

const keyword = ref('')

function doSearch() {
  const kw = keyword.value.trim()
  router.push({ path: '/list', query: kw ? { keyword: kw } : {} })
}

async function goCart() {
  const ok = await ui.requireLogin()
  if (ok) router.push('/cart')
}

function userClick() {
  if (auth.isLoggedIn) {
    auth.logout()
    cart.clearLocal()
    toast('已退出登录')
  } else {
    ui.openLogin()
  }
}
</script>
