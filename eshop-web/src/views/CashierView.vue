<template>
  <section>
    <div class="crumb"><a @click="router.push('/orders')">我的订单</a><span>/</span>收银台</div>

    <div class="cashier" v-if="order">
      <template v-if="order.status === 0">
        <div class="pay-label">应付金额</div>
        <div class="pay-amount"><small>¥</small>{{ fmt(order.payAmount) }}</div>
        <div class="pay-no">订单号：{{ order.orderNo }}</div>
        <div class="pay-qr">
          <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="#C9CDD4" stroke-width="1.8"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><path d="M14 14h3v3h-3zM19 19h2v2h-2z"/></svg>
          <span>模拟收款码</span>
        </div>
        <p style="color:var(--text-3);font-size:13px;margin-bottom:20px">
          演示环境：点击下方按钮模拟第三方支付平台回调（对应接口 9.2）
        </p>
        <div style="display:flex;gap:12px;justify-content:center">
          <button class="btn btn-primary" style="padding:0 36px" :disabled="paying" @click="pay(true)">模拟支付成功</button>
          <button class="btn btn-ghost" :disabled="paying" @click="pay(false)">模拟支付失败</button>
        </div>
      </template>

      <template v-else-if="order.status === 1">
        <div class="pay-ok">✓</div>
        <h2 style="font-size:20px;font-weight:700;color:#00B578">支付成功</h2>
        <p style="color:var(--text-3);font-size:14px;margin:8px 0 26px">
          已支付 ¥{{ fmt(order.payAmount) }} · {{ order.payTime }}
        </p>
        <div style="display:flex;gap:12px;justify-content:center">
          <button class="btn btn-primary" @click="router.push(`/orders/${order.orderNo}`)">查看订单详情</button>
          <button class="btn btn-ghost" @click="router.push('/list')">继续逛逛</button>
        </div>
      </template>

      <template v-else>
        <p style="color:var(--text-3);margin-bottom:20px">该订单当前状态为「{{ order.statusText }}」，不可支付</p>
        <button class="btn btn-ghost" @click="router.push(`/orders/${order.orderNo}`)">查看订单详情</button>
      </template>
    </div>

    <div v-else class="loading">加载中…</div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { orderApi, payApi } from '../api'
import { toast } from '../utils/toast'
import { fmt } from '../utils/format'

const route = useRoute()
const router = useRouter()

const order = ref(null)
const paymentNo = ref('')
const paying = ref(false)

onMounted(load)

async function load() {
  const orderNo = route.params.orderNo
  try {
    order.value = await orderApi.detail(orderNo)
  } catch (e) {
    router.replace('/orders')
    return
  }
  if (order.value.status === 0) {
    // 创建（或幂等复用）支付单，拿到模拟回调所需的支付单号
    const pay = await payApi.create(orderNo)
    paymentNo.value = pay.paymentNo
  }
}

async function pay(success) {
  paying.value = true
  try {
    await payApi.callback({
      paymentNo: paymentNo.value,
      outTradeNo: 'MOCK-' + paymentNo.value,
      success
    })
    if (success) {
      toast('支付成功')
      await load()
    } else {
      toast('支付失败，可重新发起')
    }
  } catch (e) {
    /* 业务错误已由拦截器提示 */
  } finally {
    paying.value = false
  }
}
</script>
