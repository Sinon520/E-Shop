<template>
  <section>
    <div class="crumb"><a @click="router.push('/')">首页</a><span>/</span>购物车</div>

    <div v-if="!cart.loaded" class="loading">加载中…</div>

    <template v-else-if="cart.items.length">
      <div class="cart-card">
        <div class="cart-head">
          <span class="check" :class="{ on: allChecked }" @click="toggleAll"></span>
          <span>商品信息</span><span>单价</span><span>数量</span><span>小计</span><span>操作</span>
        </div>
        <div class="cart-row" v-for="item in cart.items" :key="item.skuId">
          <span class="check" :class="{ on: item.checked }" @click="toggleCheck(item)">✓</span>
          <div class="cart-goods">
            <ProductImage :id="item.spuId" img-class="cart-img" />
            <div style="min-width:0">
              <div class="cart-name" @click="router.push(`/detail/${item.spuId}`)">{{ item.name }}</div>
              <div class="cart-spec">{{ item.specText }}</div>
            </div>
          </div>
          <div class="cart-price">¥{{ fmt(item.price) }}</div>
          <div class="stepper" style="height:36px;width:fit-content">
            <button @click="changeQty(item, -1)">−</button><b>{{ item.quantity }}</b><button @click="changeQty(item, 1)">＋</button>
          </div>
          <div class="cart-sum">¥{{ fmt(item.price * item.quantity) }}</div>
          <span class="del" @click="removeItem(item)">删除</span>
        </div>
      </div>

      <div class="cart-bar">
        <div class="container cart-bar-in">
          <div class="left">
            <span class="check" :class="{ on: allChecked }" @click="toggleAll"></span>
            <span>全选</span>
            <span class="del" style="margin-left:8px" @click="removeChecked">删除选中</span>
          </div>
          <div class="total">
            已选 <b style="font-size:15px;color:var(--primary)">{{ checkedCount }}</b> 件，合计：
            <b><small>¥</small><span>{{ fmt(checkedTotal) }}</span></b>
          </div>
          <button class="btn btn-primary" style="height:46px;padding:0 36px" @click="goCheckout">去结算</button>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="cart-card">
        <div class="empty">
          <svg viewBox="0 0 64 64"><circle cx="24" cy="54" r="4"/><circle cx="52" cy="54" r="4"/><path d="M6 8h6l7 34a5 5 0 0 0 5 4h25a5 5 0 0 0 5-4l5-24H16"/></svg>
          <p>购物车还是空的，去挑点好东西吧</p>
          <button class="btn btn-primary" @click="router.push('/list')">去逛逛</button>
        </div>
      </div>
    </template>
    <div style="height:40px"></div>
  </section>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import ProductImage from '../components/ProductImage.vue'
import { useCartStore } from '../stores/cart'
import { toast } from '../utils/toast'
import { fmt } from '../utils/format'

const router = useRouter()
const cart = useCartStore()

const allChecked = computed(() => cart.items.length > 0 && cart.items.every((i) => i.checked))
const checkedItems = computed(() => cart.items.filter((i) => i.checked))
const checkedCount = computed(() => checkedItems.value.reduce((n, i) => n + i.quantity, 0))
const checkedTotal = computed(() => checkedItems.value.reduce((s, i) => s + i.price * i.quantity, 0))

onMounted(() => {
  cart.fetch().catch(() => {})
})

async function toggleCheck(item) {
  await cart.updateChecked(item.skuId, !item.checked)
}

async function toggleAll() {
  await cart.checkAll(!allChecked.value)
}

async function changeQty(item, delta) {
  const next = item.quantity + delta
  if (next < 1 || next > 99) return
  await cart.updateQuantity(item.skuId, next)
}

async function removeItem(item) {
  await cart.remove(item.skuId)
}

async function removeChecked() {
  if (!checkedItems.value.length) return toast('还没有选中商品')
  await cart.batchRemove(checkedItems.value.map((i) => i.skuId))
}

function goCheckout() {
  if (!checkedCount.value) return toast('请先勾选要结算的商品')
  router.push({ path: '/checkout', query: { mode: 'cart' } })
}
</script>
