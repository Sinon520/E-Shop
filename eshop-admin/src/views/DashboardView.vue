<template>
  <div>
    <!-- ==================== 统计卡 ==================== -->
    <div class="stat-grid">
      <div class="stat-card" v-for="card in statCards" :key="card.label">
        <div class="lbl">{{ card.label }}</div>
        <div class="val">{{ card.value }}</div>
        <div class="delta" :class="card.deltaClass">{{ card.delta }}</div>
      </div>
    </div>

    <!-- ==================== 趋势 + 状态分布 ==================== -->
    <div class="dash-grid">
      <div class="panel">
        <div class="panel-hd"><h2>销售趋势<span class="sub">近 7 天成交金额（元）</span></h2></div>
        <div ref="trendRef" class="chart"></div>
      </div>
      <div class="panel">
        <div class="panel-hd"><h2>订单状态分布</h2></div>
        <div style="display: flex; align-items: center; gap: 18px">
          <div ref="statusRef" style="width: 170px; height: 170px; flex: none"></div>
          <div style="flex: 1">
            <div class="legend-row" v-for="item in statusLegend" :key="item.status">
              <span class="legend-dot" :style="{ background: item.color }"></span>
              <span class="legend-name">{{ item.text }}</span>
              <b class="num">{{ item.cnt }}</b>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 热销榜 + 待处理 ==================== -->
    <div class="dash-grid">
      <div class="panel">
        <div class="panel-hd"><h2>热销商品 TOP5<span class="sub">按销量</span></h2></div>
        <div v-if="hotTop5.length">
          <div class="rank-row" v-for="(item, index) in hotTop5" :key="item.name">
            <div class="rank-no">{{ index + 1 }}</div>
            <div class="thumb-img" :style="{ background: thumbColor(index) }">{{ item.name?.charAt(0) }}</div>
            <div class="rank-name">{{ item.name }}</div>
            <div class="rank-val num">{{ item.qty }}<small>件</small></div>
          </div>
        </div>
        <el-empty v-else description="暂无成交商品" :image-size="60" />
      </div>

      <div class="panel">
        <div class="panel-hd"><h2>待处理事项</h2></div>
        <div
          class="rank-row todo-row"
          v-for="item in todos"
          :key="item.label"
          @click="go(item.page)"
        >
          <span class="el-tag" :class="`tag-${item.tag}`">{{ item.label }}</span>
          <div class="rank-name"></div>
          <div class="rank-val num">{{ item.value }} ›</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getDashboard, getSpuPage, getStockPage } from '../api'
import { ORDER_STATUS } from '../utils/constants'
import { formatAmount, formatMoney } from '../utils/format'

const router = useRouter()

const dashboard = ref(null)
const lowStockTotal = ref(0)
const offShelfTotal = ref(0)
const trendRef = ref(null)
const statusRef = ref(null)

let trendChart = null
let statusChart = null

/** 统计卡（环比 = 对比前一日，取自 trend7d 最后两条） */
const statCards = computed(() => {
  const d = dashboard.value || {}
  const trend = d.trend7d || []
  const today = trend.length ? trend[trend.length - 1] : null
  const prev = trend.length > 1 ? trend[trend.length - 2] : null

  const salesDelta = buildDelta(today && Number(today.amount), prev && Number(prev.amount), true)
  const ordersDelta = buildDelta(today && Number(today.orders), prev && Number(prev.orders), false)

  return [
    { label: '今日销售额', value: formatMoney(d.todaySales), ...salesDelta },
    { label: '今日订单数', value: `${d.todayOrders ?? 0}`, ...ordersDelta },
    { label: '待发货订单', value: `${d.pendingShip ?? 0}`, delta: (d.pendingShip ?? 0) > 0 ? '需尽快处理' : '暂无待处理', deltaClass: (d.pendingShip ?? 0) > 0 ? 'flat' : 'up' },
    { label: '累计成交金额', value: formatMoney(d.totalSales), delta: '自平台上线累计', deltaClass: 'flat' }
  ]
})

/** 环比前一日：金额差 + 百分比（无前一日数据时展示占位） */
function buildDelta(todayVal, prevVal, isMoney) {
  if (todayVal == null || prevVal == null || prevVal === 0) {
    return { delta: '暂无环比数据', deltaClass: 'flat' }
  }
  const diff = todayVal - prevVal
  if (diff === 0) return { delta: '环比前一日持平', deltaClass: 'flat' }
  const pct = Math.abs((diff / prevVal) * 100).toFixed(1)
  const amountPart = isMoney ? `¥${formatAmount(Math.abs(diff))} ` : ''
  return {
    delta: `环比前一日 ${diff > 0 ? '↑' : '↓'} ${amountPart}${diff > 0 ? '+' : '-'}${pct}%`,
    deltaClass: diff > 0 ? 'up' : 'down'
  }
}

/** 状态分布图例 */
const statusLegend = computed(() => {
  const dist = dashboard.value?.statusDist || []
  const map = new Map(dist.map((item) => [Number(item.status), Number(item.cnt)]))
  return [0, 1, 2, 3, 4, 5]
    .filter((status) => map.get(status) > 0)
    .map((status) => ({ status, cnt: map.get(status), text: ORDER_STATUS[status].text, color: ORDER_STATUS[status].color }))
})

const hotTop5 = computed(() => dashboard.value?.hotTop5 || [])

const todos = computed(() => [
  { label: '待发货订单', value: `${dashboard.value?.pendingShip ?? 0} 单`, page: '/orders', tag: 'orange' },
  { label: '低库存 SKU', value: `${lowStockTotal.value} 条`, page: '/stock', tag: 'red' },
  { label: '已下架商品', value: `${offShelfTotal.value} 个`, page: '/goods', tag: 'gray' }
])

const THUMB_COLORS = ['#FFF3EF', '#EAF2FE', '#E8F7EF', '#FDF3E7', '#F0F0FE']
function thumbColor(index) {
  return THUMB_COLORS[index % THUMB_COLORS.length]
}

function go(page) {
  router.push(page)
}

function renderTrend() {
  const trend = dashboard.value?.trend7d || []
  const days = trend.map((item) => String(item.d ?? '').slice(5, 10))
  const amounts = trend.map((item) => Number(item.amount ?? 0))

  trendChart?.setOption({
    grid: { left: 52, right: 18, top: 22, bottom: 28 },
    tooltip: { trigger: 'axis', valueFormatter: (v) => `¥ ${formatAmount(v)}` },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: days,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#ECEEF1' } },
      axisLabel: { color: '#9AA1AC', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#F0F1F3' } },
      axisLabel: { color: '#9AA1AC', fontSize: 11 }
    },
    series: [
      {
        type: 'line',
        data: amounts,
        symbol: 'circle',
        symbolSize: 7,
        itemStyle: { color: '#fff', borderColor: '#EE4D2D', borderWidth: 2 },
        lineStyle: { color: '#EE4D2D', width: 2.4 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(238,77,45,.18)' },
            { offset: 1, color: 'rgba(238,77,45,0)' }
          ])
        }
      }
    ]
  })
}

function renderStatus() {
  const legend = statusLegend.value
  const total = legend.reduce((sum, item) => sum + item.cnt, 0)

  statusChart?.setOption({
    tooltip: { trigger: 'item', formatter: '{b}：{c} 单' },
    graphic: [
      { type: 'text', left: 'center', top: '40%', style: { text: String(total), fontSize: 24, fontWeight: 700, fill: '#1F2329', textAlign: 'center' } },
      { type: 'text', left: 'center', top: '58%', style: { text: '全部订单', fontSize: 11, fill: '#9AA1AC', textAlign: 'center' } }
    ],
    series: [
      {
        type: 'pie',
        radius: ['62%', '80%'],
        center: ['50%', '50%'],
        label: { show: false },
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        data: legend.map((item) => ({ name: item.text, value: item.cnt, itemStyle: { color: item.color } }))
      }
    ]
  })
}

async function load() {
  try {
    const [data, lowStock, offShelf] = await Promise.all([
      getDashboard(),
      getStockPage({ page: 1, size: 1, lowStock: true }).catch(() => ({ total: 0 })),
      getSpuPage({ page: 1, size: 1, status: 0 }).catch(() => ({ total: 0 }))
    ])
    dashboard.value = data
    lowStockTotal.value = lowStock?.total ?? 0
    offShelfTotal.value = offShelf?.total ?? 0
    renderTrend()
    renderStatus()
  } catch {
    /* 拦截器已提示 */
  }
}

function handleResize() {
  trendChart?.resize()
  statusChart?.resize()
}

onMounted(() => {
  trendChart = echarts.init(trendRef.value)
  statusChart = echarts.init(statusRef.value)
  window.addEventListener('resize', handleResize)
  load()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  statusChart?.dispose()
})
</script>

<style scoped>
.todo-row { cursor: pointer; }
.todo-row:hover .rank-val { color: var(--primary); }
.tag-orange { background: var(--orange-light); color: var(--orange); }
.tag-red { background: var(--red-light); color: var(--red); }
.tag-gray { background: #F0F1F3; color: var(--text-2); }
</style>
