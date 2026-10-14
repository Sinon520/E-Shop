<template>
  <section v-if="spu">
    <div class="crumb">
      <a @click="router.push('/')">首页</a><span>/</span>
      <a @click="router.push('/list')">全部商品</a><span>/</span>
      <span>{{ spu.name.split('｜')[0] }}</span>
    </div>

    <div class="detail">
      <div class="d-gallery">
        <ProductImage :id="spu.id" img-class="big" />
        <div class="thumbs">
          <div
            class="thumb"
            v-for="i in 4"
            :key="i"
            :class="{ on: thumb === i }"
            :style="thumbBg"
            @click="thumb = i"
          >
            <svg viewBox="0 0 64 64" fill="none" :stroke="thumbStroke" stroke-linecap="round" stroke-linejoin="round" v-html="ph.icon"></svg>
          </div>
        </div>
      </div>

      <div class="d-info">
        <h1>{{ spu.name }}</h1>
        <div class="d-tags"><span class="d-tag">限时特惠</span><span class="d-tag">包邮</span><span class="d-tag">七天无理由</span></div>
        <div class="price-box">
          <div class="price">
            <small>¥</small>{{ fmt(currentSku?.price) }}
            <span class="old" v-if="currentSku?.marketPrice">¥{{ fmt(currentSku.marketPrice) }}</span>
          </div>
          <div class="meta">
            <span>已售 {{ fmt(spu.sales) }} 件</span>
            <span>运费：免运费</span>
            <span>{{ stockText }}</span>
          </div>
        </div>

        <div class="spec" v-for="g in spu.specs" :key="g.name">
          <div class="spec-label">{{ g.name }}</div>
          <div class="spec-opts">
            <span
              class="opt"
              v-for="v in g.values"
              :key="v"
              :class="{ on: selected[g.name] === v }"
              @click="pickSpec(g.name, v)"
            >{{ v }}</span>
          </div>
        </div>

        <div class="buy-row">
          <div class="stepper">
            <button @click="qtyChange(-1)">−</button><b>{{ qty }}</b><button @click="qtyChange(1)">＋</button>
          </div>
          <span style="color:var(--text-3);font-size:13px">{{ stockText }}</span>
          <button class="btn btn-ghost" :disabled="!purchasable" @click="addToCart">加入购物车</button>
          <button class="btn btn-primary" :disabled="!purchasable" @click="buyNow">立即购买</button>
        </div>

        <div class="after">
          <span><i>✓</i>正品保障</span><span><i>✓</i>48 小时内发货</span><span><i>✓</i>七天无理由退换</span><span><i>✓</i>假一赔十</span>
        </div>
      </div>
    </div>

    <div class="d-tabs">
      <div class="tab-hd">
        <div class="tab" :class="{ on: tab === 'desc' }" @click="tab = 'desc'">商品详情</div>
        <div class="tab" :class="{ on: tab === 'param' }" @click="tab = 'param'">规格参数</div>
        <div class="tab" :class="{ on: tab === 'review' }" @click="tab = 'review'">用户评价</div>
      </div>
      <div class="tab-bd">
        <div class="tab-pane" v-show="tab === 'desc'" v-html="spu.detail || '<p>暂无详情</p>'"></div>
        <div class="tab-pane" v-show="tab === 'param'">
          <table class="param-table">
            <tbody>
              <tr><td>品牌</td><td>{{ spu.brandName || '—' }}</td></tr>
              <tr><td>分类</td><td>{{ spu.categoryName || '—' }}</td></tr>
              <tr v-for="g in spu.specs" :key="g.name"><td>{{ g.name }}</td><td>{{ selected[g.name] || '—' }}</td></tr>
              <tr><td>SKU 编码</td><td>{{ currentSku?.skuCode || '—' }}</td></tr>
            </tbody>
          </table>
        </div>
        <div class="tab-pane" v-show="tab === 'review'">
          <div class="empty" style="padding:34px 0">
            <svg viewBox="0 0 64 64"><path d="M12 14h40v28H30l-10 8v-8H12z"/><path d="M24 26h16M24 34h10"/></svg>
            <p>评价功能规划中（P4）</p>
          </div>
        </div>
      </div>
    </div>
    <div style="height:50px"></div>
  </section>

  <div v-else-if="error" class="sec">
    <div class="empty">
      <svg viewBox="0 0 64 64"><path d="M14 40 v-6 a18 18 0 0 1 36 0 v6"/><rect x="10" y="38" width="10" height="16" rx="4"/><rect x="44" y="38" width="10" height="16" rx="4"/></svg>
      <p>商品不存在或已下架</p>
      <button class="btn btn-primary" @click="router.push('/list')">去逛逛别的</button>
    </div>
  </div>

  <div v-else class="loading">加载中…</div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProductImage from '../components/ProductImage.vue'
import { productApi, stockApi } from '../api'
import { useCartStore } from '../stores/cart'
import { useUiStore } from '../stores/ui'
import { toast } from '../utils/toast'
import { fmt } from '../utils/format'
import { placeholderFor, imageBg, imageStroke } from '../utils/placeholder'

const route = useRoute()
const router = useRouter()
const cart = useCartStore()
const ui = useUiStore()

const spu = ref(null)
const error = ref(false)
const selected = reactive({})
const qty = ref(1)
const stock = ref(null)
const thumb = ref(1)
const tab = ref('desc')

const ph = computed(() => placeholderFor(Number(spu.value?.id || 0)))
const thumbBg = computed(() => imageBg(ph.value.hue))
const thumbStroke = computed(() => imageStroke(ph.value.hue))

/** 当前所选规格组合对应的 SKU */
const currentSku = computed(() => {
  if (!spu.value) return null
  const groups = spu.value.specs.map((g) => g.name)
  return (
    spu.value.skus.find((s) => groups.every((g) => s.specJson?.[g] === selected[g])) || null
  )
})

const purchasable = computed(() => !!currentSku.value && currentSku.value.status === 1 && stock.value !== 0)

const stockText = computed(() => {
  if (!currentSku.value || currentSku.value.status !== 1) return '该组合暂不可选'
  if (stock.value === null) return '库存查询中…'
  return `库存 ${stock.value} 件`
})

watch(currentSku, async (sku) => {
  stock.value = null
  if (!sku || sku.status !== 1) return
  try {
    stock.value = (await stockApi.available(sku.id)).available
  } catch (e) {
    stock.value = 0
  }
})

function pickSpec(name, value) {
  selected[name] = value
  qty.value = 1
}

function qtyChange(delta) {
  const max = Math.min(99, stock.value ?? 99)
  qty.value = Math.min(Math.max(1, qty.value + delta), Math.max(1, max))
}

async function addToCart() {
  const ok = await ui.requireLogin()
  if (!ok || !currentSku.value) return
  try {
    await cart.add(currentSku.value.id, qty.value)
    toast('已加入购物车')
  } catch (e) {}
}

async function buyNow() {
  const ok = await ui.requireLogin()
  if (!ok || !currentSku.value) return
  router.push({
    path: '/checkout',
    query: { mode: 'direct', spuId: spu.value.id, skuId: currentSku.value.id, qty: qty.value }
  })
}

onMounted(async () => {
  try {
    const data = await productApi.spuDetail(route.params.id)
    spu.value = data
    // 默认选中价格最低的在架 SKU
    const first =
      data.skus
        .filter((s) => s.status === 1)
        .sort((a, b) => a.price - b.price)[0] || data.skus[0]
    if (first?.specJson) Object.assign(selected, first.specJson)
  } catch (e) {
    error.value = true
  }
})
</script>
