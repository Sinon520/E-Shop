<template>
  <section>
    <div class="banner">
      <div class="banner-txt">
        <span class="banner-tag">开学焕新季 · 限时优惠</span>
        <h1>数码好物<br/>全场低至 5 折</h1>
        <p>正品保障 · 极速发货 · 七天无理由退换</p>
        <button class="btn" @click="router.push('/list')">立即抢购 →</button>
      </div>
      <div class="banner-deco">
        <div class="c1"></div><div class="c2"></div>
        <svg viewBox="0 0 64 64"><path d="M14 40 v-6 a18 18 0 0 1 36 0 v6"/><rect x="10" y="38" width="10" height="16" rx="4"/><rect x="44" y="38" width="10" height="16" rx="4"/></svg>
      </div>
    </div>

    <div class="cats">
      <div class="cat" v-for="c in catEntries" :key="c.key" @click="goCat(c)">
        <div class="cat-ico" :style="catBg(c)">
          <svg viewBox="0 0 64 64" :stroke="catStroke(c)" v-html="c.icon"></svg>
        </div>
        <span>{{ c.name }}</span>
      </div>
    </div>

    <div class="sec">
      <div class="sec-hd">
        <h2>热卖推荐<small>大家都在买</small></h2>
        <a @click="router.push('/list')">查看全部 →</a>
      </div>
      <div v-if="loading" class="loading">加载中…</div>
      <div v-else class="grid">
        <ProductCard v-for="p in hot" :key="p.id" :product="p" />
      </div>
    </div>

    <div class="services">
      <div class="svc"><svg viewBox="0 0 24 24"><path d="M12 2 4 6v6c0 5 3.4 8.6 8 10 4.6-1.4 8-5 8-10V6l-8-4z"/><path d="m8.5 12 2.4 2.4L16 10"/></svg><span><b>正品保障</b><br/>官方授权渠道</span></div>
      <div class="svc"><svg viewBox="0 0 24 24"><path d="M3 7h11v9H3z"/><path d="M14 10h4l3 3v3h-7z"/><circle cx="7" cy="18" r="1.8"/><circle cx="17" cy="18" r="1.8"/></svg><span><b>极速发货</b><br/>48 小时内出库</span></div>
      <div class="svc"><svg viewBox="0 0 24 24"><path d="M12 3v18"/><path d="M5 8h14"/><path d="M7 8 4 14a3.2 3.2 0 0 0 6 0L7 8zM17 8l-3 6a3.2 3.2 0 0 0 6 0l-3-6z"/></svg><span><b>七天无理由</b><br/>退换无忧</span></div>
      <div class="svc"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 3"/></svg><span><b>售后无忧</b><br/>专属客服响应</span></div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { productApi } from '../api'
import ProductCard from '../components/ProductCard.vue'
import { categoryStyle } from '../utils/placeholder'

const router = useRouter()
const categories = ref([])
const hot = ref([])
const loading = ref(true)

/** 分类入口 = 前 5 个真实分类 + 前端追加「全部商品」（契约 §3.1） */
const catEntries = computed(() => {
  const list = categories.value.slice(0, 5).map((c) => ({
    key: 'c' + c.id,
    id: c.id,
    name: c.name,
    ...categoryStyle(c.id)
  }))
  list.push({ key: 'all', id: null, name: '全部商品', ...categoryStyle(0) })
  return list
})

function catBg(c) {
  return { background: `linear-gradient(135deg,hsl(${c.hue},70%,92%),hsl(${c.hue},75%,84%))` }
}
function catStroke(c) {
  return `hsl(${c.hue},45%,42%)`
}
function goCat(c) {
  if (c.id) {
    router.push({ path: '/list', query: { categoryId: c.id } })
  } else {
    router.push('/list')
  }
}

onMounted(async () => {
  try {
    const [cats, page] = await Promise.all([
      productApi.categories(),
      productApi.spuPage({ sort: 'sales', size: 8 })
    ])
    categories.value = cats
    hot.value = page.list
  } finally {
    loading.value = false
  }
})
</script>
