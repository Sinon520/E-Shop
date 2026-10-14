<template>
  <section>
    <div class="crumb"><a @click="router.push('/orders')">我的订单</a><span>/</span>订单详情</div>

    <div v-if="o" style="margin-top:14px">
      <div class="panel od-status">
        <div class="badge" :class="badgeClass(o.status)" style="font-size:15px;padding:6px 20px">{{ o.statusText }}</div>
        <div style="color:var(--text-3);font-size:13px;margin-top:8px">{{ statusHint }}</div>
      </div>

      <div class="panel">
        <div class="panel-title">收货信息</div>
        <div style="font-weight:600">
          {{ o.receiverName }}　<span style="font-weight:400;color:var(--text-2)">{{ o.receiverPhone }}</span>
        </div>
        <div style="color:var(--text-2);margin-top:6px">{{ o.receiverAddress }}</div>
      </div>

      <div class="panel">
        <div class="panel-title">商品清单</div>
        <div class="co-item" v-for="it in o.items" :key="it.skuId">
          <ProductImage :id="it.spuId" img-class="co-img" />
          <div style="flex:1;min-width:0">
            <div class="cart-name">{{ it.name }}</div>
            <div class="cart-spec">{{ it.specText }}</div>
          </div>
          <div class="cart-price">¥{{ fmt(it.price) }} × {{ it.quantity }}</div>
          <div class="cart-sum">¥{{ fmt(it.totalAmount) }}</div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-title">订单信息</div>
        <div class="sum-row"><span>订单号</span><span>{{ o.orderNo }}</span></div>
        <div class="sum-row"><span>创建时间</span><span>{{ o.createTime }}</span></div>
        <div class="sum-row" v-if="o.payTime"><span>支付时间</span><span>{{ o.payTime }}</span></div>
        <div class="sum-row" v-if="o.remark"><span>订单备注</span><span>{{ o.remark }}</span></div>
        <div class="sum-total"><span>应付金额</span><b><small>¥</small>{{ fmt(o.payAmount) }}</b></div>
        <div v-if="o.status === 0" style="display:flex;gap:10px;justify-content:flex-end;margin-top:14px">
          <button class="btn btn-ghost" @click="cancelOrder">取消订单</button>
          <button class="btn btn-primary" @click="router.push(`/cashier/${o.orderNo}`)">去支付</button>
        </div>
      </div>
    </div>

    <div v-else class="loading">加载中…</div>
    <div style="height:40px"></div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProductImage from '../components/ProductImage.vue'
import { orderApi } from '../api'
import { toast } from '../utils/toast'
import { fmt } from '../utils/format'

const route = useRoute()
const router = useRouter()

const o = ref(null)

const badgeClass = (status) => {
  const map = { 0: 'pending', 1: 'paid', 2: 'shipped', 3: 'paid', 4: 'cancelled', 5: 'cancelled' }
  return map[status] || 'cancelled'
}

const statusHint = computed(() => {
  if (!o.value) return ''
  const hints = {
    0: '请尽快完成支付，超时订单将自动关闭',
    1: o.value.payTime ? '支付时间：' + o.value.payTime : '',
    2: '商品已发出，请注意查收',
    3: '交易已完成，感谢惠顾',
    4: '订单已取消，如需购买请重新下单',
    5: '订单超时未支付，已自动关闭'
  }
  return hints[o.value.status] || ''
})

onMounted(load)

async function load() {
  try {
    o.value = await orderApi.detail(route.params.orderNo)
  } catch (e) {
    router.replace('/orders')
  }
}

async function cancelOrder() {
  try {
    await orderApi.cancel(o.value.orderNo)
    toast('订单已取消，库存已回补')
    load()
  } catch (e) {
    /* 业务错误已由拦截器提示 */
  }
}
</script>
