<template>
  <div class="pcard" @click="router.push(`/detail/${product.id}`)">
    <ProductImage :id="product.id" img-class="pimg" />
    <div class="pbody">
      <div class="pname">{{ product.name }}</div>
      <div class="prow">
        <div>
          <div class="price">
            <small>¥</small>{{ fmt(product.price) }}
            <span class="old" v-if="product.marketPrice">¥{{ fmt(product.marketPrice) }}</span>
          </div>
          <div class="psales">已售 {{ fmt(product.sales) }}</div>
        </div>
        <button class="add-btn" title="加入购物车" @click.stop="quickAdd">＋</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import ProductImage from './ProductImage.vue'
import { productApi } from '../api'
import { useCartStore } from '../stores/cart'
import { useUiStore } from '../stores/ui'
import { toast } from '../utils/toast'
import { fmt } from '../utils/format'

const props = defineProps({
  product: { type: Object, required: true }
})

const router = useRouter()
const cart = useCartStore()
const ui = useUiStore()

/** 快捷加购：列表项只有 SPU，取详情里价格最低的在架 SKU 加购 1 件 */
async function quickAdd() {
  const ok = await ui.requireLogin()
  if (!ok) return
  try {
    const detail = await productApi.spuDetail(props.product.id)
    const sku = (detail.skus || []).filter((s) => s.status === 1).sort((a, b) => a.price - b.price)[0]
    if (!sku) return toast('商品已下架')
    await cart.add(sku.id, 1)
    toast('已加入购物车')
  } catch (e) {
    /* 业务错误已由拦截器提示 */
  }
}
</script>
