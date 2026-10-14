<template>
  <section>
    <div class="crumb"><a @click="back">返回</a><span>/</span>确认订单</div>

    <div v-if="items.length" class="checkout">
      <div>
        <div class="panel">
          <div class="panel-title">收货地址</div>
          <div class="addr-grid">
            <div
              class="addr-card"
              v-for="a in addresses"
              :key="a.id"
              :class="{ on: a.id === selAddrId }"
              @click="selAddrId = a.id"
            >
              <div class="addr-name">
                {{ a.receiverName }}
                <span class="tel">{{ a.receiverPhone }}</span>
                <span class="addr-default" v-if="a.isDefault === 1">默认</span>
              </div>
              <div class="addr-meta">{{ (a.province || '') + (a.city || '') + (a.district || '') }} {{ a.detailAddress }}</div>
            </div>
            <div class="addr-add" @click="addrModal.open()">＋ 新增地址</div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-title">商品清单</div>
          <div class="co-item" v-for="it in items" :key="it.skuId">
            <ProductImage :id="it.spuId" img-class="co-img" />
            <div style="flex:1;min-width:0">
              <div class="cart-name">{{ it.name }}</div>
              <div class="cart-spec">{{ it.specText }}</div>
            </div>
            <div class="cart-price">¥{{ fmt(it.price) }} × {{ it.quantity }}</div>
            <div class="cart-sum">¥{{ fmt(it.price * it.quantity) }}</div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-title">订单备注</div>
          <input v-model="remark" class="remark-input" placeholder="选填：如对配送时间有要求，请在此备注" maxlength="100" />
        </div>
      </div>

      <div class="sum-card">
        <div class="sum-row"><span>商品金额</span><span>¥{{ fmt(total) }}</span></div>
        <div class="sum-row"><span>运费</span><span>免运费</span></div>
        <div class="sum-total"><span>应付金额</span><b><small>¥</small>{{ fmt(total) }}</b></div>
        <button class="btn btn-primary" style="width:100%;margin-top:18px" :disabled="submitting" @click="submit">
          {{ submitting ? '提交中…' : '提交订单' }}
        </button>
      </div>
    </div>

    <div v-else class="loading">加载中…</div>
    <div style="height:40px"></div>

    <AddressModal ref="addrModal" @saved="onAddrSaved" />
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AddressModal from '../components/AddressModal.vue'
import ProductImage from '../components/ProductImage.vue'
import { addressApi, cartApi, orderApi, productApi } from '../api'
import { useCartStore } from '../stores/cart'
import { toast } from '../utils/toast'
import { fmt } from '../utils/format'

const route = useRoute()
const router = useRouter()
const cart = useCartStore()

const addrModal = ref(null)
const addresses = ref([])
const selAddrId = ref(null)
const items = ref([])
const remark = ref('')
const submitting = ref(false)

const total = computed(() => items.value.reduce((s, i) => s + i.price * i.quantity, 0))

onMounted(async () => {
  await loadAddresses()
  if (route.query.mode === 'direct') {
    await loadDirectItem()
  } else {
    await loadCartItems()
  }
})

async function loadAddresses() {
  addresses.value = await addressApi.list()
  const stillExists = addresses.value.some((a) => a.id === selAddrId.value)
  if (!stillExists) {
    const def = addresses.value.find((a) => a.isDefault === 1) || addresses.value[0]
    selAddrId.value = def ? def.id : null
  }
}

/** 购物车结算：取服务端已勾选条目 */
async function loadCartItems() {
  const list = await cartApi.list()
  const checked = list.filter((i) => i.checked)
  if (!checked.length) {
    toast('请先勾选要结算的商品')
    router.replace('/cart')
    return
  }
  items.value = checked.map((i) => ({
    skuId: i.skuId,
    spuId: i.spuId,
    name: i.name,
    specText: i.specText,
    price: i.price,
    quantity: i.quantity
  }))
}

/** 立即购买：按 SKU 取商品信息 */
async function loadDirectItem() {
  const { spuId, skuId, qty } = route.query
  try {
    const detail = await productApi.spuDetail(spuId)
    const sku = detail.skus.find((s) => String(s.id) === String(skuId))
    if (!sku) {
      toast('商品规格不存在')
      router.replace('/list')
      return
    }
    items.value = [
      {
        skuId: sku.id,
        spuId: detail.id,
        name: detail.name,
        specText: sku.name,
        price: sku.price,
        quantity: Math.min(99, Math.max(1, Number(qty) || 1))
      }
    ]
  } catch (e) {
    router.replace('/list')
  }
}

function onAddrSaved(id) {
  selAddrId.value = id
  loadAddresses()
}

function back() {
  window.history.length > 1 ? router.back() : router.push('/cart')
}

async function submit() {
  if (!selAddrId.value) return toast('请先选择收货地址')
  submitting.value = true
  try {
    const data = await orderApi.create({
      addressId: selAddrId.value,
      remark: remark.value || undefined,
      items: items.value.map((i) => ({ skuId: i.skuId, quantity: i.quantity }))
    })
    if (route.query.mode !== 'direct') {
      cart.fetch().catch(() => {})
    }
    toast('订单提交成功，请完成支付')
    router.push(`/cashier/${data.orderNo}`)
  } catch (e) {
    /* 3002 库存不足等业务错误已由拦截器提示 */
  } finally {
    submitting.value = false
  }
}
</script>
