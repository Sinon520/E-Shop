<template>
  <section>
    <div class="crumb">
      <a @click="router.push('/')">首页</a><span>/</span>
      <template v-if="keyword">搜索「{{ keyword }}」</template>
      <template v-else>全部商品</template>
    </div>

    <div class="filter">
      <div class="filter-row">
        <span class="filter-label">分类</span>
        <span class="chip" :class="{ on: !query.categoryId }" @click="setQuery({ categoryId: undefined })">全部</span>
        <span
          class="chip"
          v-for="c in categories"
          :key="c.id"
          :class="{ on: String(query.categoryId) === String(c.id) }"
          @click="setQuery({ categoryId: c.id })"
        >{{ c.name }}</span>
      </div>
      <div class="filter-row">
        <span class="filter-label">排序</span>
        <span
          class="chip sort-chip"
          v-for="s in sorts"
          :key="s.k"
          :class="{ on: (query.sort || 'default') === s.k }"
          @click="setQuery({ sort: s.k === 'default' ? undefined : s.k })"
        >{{ s.t }}</span>
        <span style="margin-left:auto;color:var(--text-3);font-size:13px">共 {{ total }} 件商品</span>
      </div>
    </div>

    <div v-if="loading" class="loading">加载中…</div>
    <div v-else-if="!list.length" class="sec">
      <div class="empty">
        <svg viewBox="0 0 64 64"><circle cx="27" cy="27" r="16"/><path d="m39 39 14 14"/></svg>
        <p>没有找到相关商品</p>
        <button class="btn btn-primary" @click="router.push('/')">回首页</button>
      </div>
    </div>
    <div v-else class="sec">
      <div class="grid"><ProductCard v-for="p in list" :key="p.id" :product="p" /></div>
    </div>

    <Pager :page="page" :total="total" :size="size" @change="setPage" />
    <div style="height:50px"></div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi } from '../api'
import ProductCard from '../components/ProductCard.vue'
import Pager from '../components/Pager.vue'

const route = useRoute()
const router = useRouter()

const sorts = computed(() => [
  { k: 'default', t: keyword.value ? '相关度' : '综合' },
  { k: 'sales', t: '销量优先' },
  { k: 'priceAsc', t: '价格 ↑' },
  { k: 'priceDesc', t: '价格 ↓' }
])

const categories = ref([])
const list = ref([])
const total = ref(0)
const loading = ref(true)
const size = 12

const query = computed(() => route.query)
const page = computed(() => Number(route.query.page) || 1)
/** 有搜索词时走 ES 搜索接口（相关度排序），否则走分类浏览列表 */
const keyword = computed(() => (route.query.keyword || '').trim())

/** query 变化（分类/排序/搜索/翻页）统一走 router，由 watcher 触发请求 */
function setQuery(patch) {
  const q = { ...route.query, ...patch }
  Object.keys(q).forEach((k) => {
    if (q[k] === undefined || q[k] === '') delete q[k]
  })
  delete q.page
  router.push({ path: '/list', query: q })
}

function setPage(p) {
  router.push({ path: '/list', query: { ...route.query, page: p } })
}

async function load() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size,
      categoryId: route.query.categoryId || undefined,
      keyword: keyword.value || undefined,
      sort: route.query.sort || undefined
    }
    const data = keyword.value
      ? await productApi.spuSearch(params)
      : await productApi.spuPage(params)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  categories.value = await productApi.categories()
})

watch(() => route.query, load, { immediate: true })
</script>
