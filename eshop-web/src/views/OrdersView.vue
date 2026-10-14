<template>
  <section>
    <div class="crumb"><a @click="router.push('/')">首页</a><span>/</span>我的订单</div>

    <div class="order-tabs">
      <span class="chip" v-for="t in tabs" :key="t.k" :class="{ on: tab === t.k }" @click="setTab(t.k)">{{ t.t }}</span>
    </div>

    <div v-if="loading" class="loading">加载中…</div>

    <template v-else-if="orders.length">
      <div class="oc" v-for="o in orders" :key="o.orderNo">
        <div class="oc-hd">
          <span>订单号：{{ o.orderNo }}　·　{{ o.createTime }}</span>
          <span class="badge" :class="badgeClass(o.status)">{{ o.statusText }}</span>
        </div>
        <div class="oc-bd" @click="router.push(`/orders/${o.orderNo}`)">
          <ProductImage v-for="it in o.items.slice(0, 3)" :key="it.skuId" :id="it.spuId" img-class="oc-thumb" />
          <span class="oc-count">共 {{ countOf(o) }} 件</span>
        </div>
        <div class="oc-ft">
          <span class="oc-amount">实付<b>¥{{ fmt(o.payAmount) }}</b></span>
          <div class="oc-actions">
            <template v-if="o.status === 0">
              <button class="btn btn-sm btn-ghost" @click="cancelOrder(o)">取消订单</button>
              <button class="btn btn-sm btn-primary" @click="router.push(`/cashier/${o.orderNo}`)">去支付</button>
            </template>
            <template v-else>
              <button class="btn btn-sm btn-ghost" @click="router.push(`/orders/${o.orderNo}`)">查看详情</button>
            </template>
          </div>
        </div>
      </div>
      <Pager :page="page" :total="total" :size="size" @change="setPage" />
    </template>

    <div v-else class="empty">
      <svg viewBox="0 0 64 64"><rect x="12" y="10" width="40" height="44" rx="6"/><path d="M22 24h20M22 32h20M22 40h12"/></svg>
      <p>暂无相关订单</p>
      <button class="btn btn-primary" @click="router.push('/')">去逛逛</button>
    </div>
    <div style="height:40px"></div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ProductImage from '../components/ProductImage.vue'
import Pager from '../components/Pager.vue'
import { orderApi } from '../api'
import { toast } from '../utils/toast'
import { fmt } from '../utils/format'

const router = useRouter()

const tabs = [
  { k: 'all', t: '全部' },
  { k: '0', t: '待支付' },
  { k: '1', t: '已支付' },
  { k: '2', t: '已发货' },
  { k: '3', t: '已完成' },
  { k: '4', t: '已取消' }
]

const tab = ref('all')
const page = ref(1)
const size = 10
const total = ref(0)
const orders = ref([])
const loading = ref(true)

/** 状态徽标色：0 橙 / 1 绿 / 2 蓝 / 3 绿 / 4、5 灰 */
function badgeClass(status) {
  const map = { 0: 'pending', 1: 'paid', 2: 'shipped', 3: 'paid', 4: 'cancelled', 5: 'cancelled' }
  return map[status] || 'cancelled'
}

function countOf(o) {
  return o.items.reduce((n, i) => n + i.quantity, 0)
}

async function load() {
  loading.value = true
  try {
    const data = await orderApi.page({
      page: page.value,
      size,
      status: tab.value === 'all' ? undefined : Number(tab.value)
    })
    orders.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function setTab(k) {
  tab.value = k
  page.value = 1
  load()
}

function setPage(p) {
  page.value = p
  load()
}

async function cancelOrder(o) {
  try {
    await orderApi.cancel(o.orderNo)
    toast('订单已取消，库存已回补')
    load()
  } catch (e) {
    /* 5002 等业务错误已由拦截器提示 */
  }
}

onMounted(load)
</script>
