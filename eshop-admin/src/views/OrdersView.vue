<template>
  <div class="panel">
    <!-- ==================== 状态 tabs ==================== -->
    <el-tabs v-model="activeTab" @tab-change="search">
      <el-tab-pane v-for="tab in ORDER_TABS" :key="tab.value" :label="tab.label" :name="tab.value" />
    </el-tabs>

    <!-- ==================== 工具条 ==================== -->
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="订单号"
        style="width: 240px"
        clearable
        @keyup.enter="search"
        @clear="search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <div class="spacer"></div>
    </div>

    <!-- ==================== 订单表格 ==================== -->
    <el-table :data="list" v-loading="loading" row-key="orderNo">
      <el-table-column label="订单号" min-width="220">
        <template #default="{ row }"><span class="num">{{ row.orderNo }}</span></template>
      </el-table-column>
      <el-table-column label="用户" width="110">
        <template #default="{ row }">
          <span v-if="row.username">{{ row.username }}</span>
          <span v-else style="color: var(--text-3)">{{ row.receiverName || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="商品" min-width="220">
        <template #default="{ row }">
          <span class="item-summary">{{ itemsSummary(row.items) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="金额" width="110" align="right">
        <template #default="{ row }"><span class="num">{{ formatMoney(row.payAmount ?? row.totalAmount) }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="ORDER_STATUS[row.status]?.tag || 'info'">{{ row.statusText || ORDER_STATUS[row.status]?.text }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下单时间" width="170">
        <template #default="{ row }"><span style="color: var(--text-2)">{{ row.createTime }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.orderNo)">详情</el-button>
          <el-button v-if="row.status === 1" link type="primary" @click="ship(row)">发货</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager-row">
      <span class="total-tip">共 {{ total }} 条</span>
      <el-pagination
        small
        background
        layout="prev, pager, next"
        :total="total"
        :page-size="query.size"
        :current-page="query.page"
        @current-change="onPageChange"
      />
    </div>

    <!-- ==================== 订单详情弹窗 ==================== -->
    <el-dialog v-model="detail.visible" :title="`订单详情 · ${detail.orderNo}`" width="640px" destroy-on-close>
      <div v-if="detail.data" v-loading="detail.loading">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px">
          <el-tag :type="ORDER_STATUS[detail.data.status]?.tag || 'info'">
            {{ detail.data.statusText || ORDER_STATUS[detail.data.status]?.text }}
          </el-tag>
          <b style="font-size: 16px" class="num">{{ formatMoney(detail.data.payAmount ?? detail.data.totalAmount) }}</b>
        </div>

        <table class="detail-tbl">
          <thead>
            <tr><th>商品</th><th class="num">单价</th><th class="num">数量</th><th class="num">小计</th></tr>
          </thead>
          <tbody>
            <tr v-for="item in detail.data.items" :key="item.skuId">
              <td>{{ item.name }}<span v-if="item.specText" style="color: var(--text-3)"> · {{ item.specText }}</span></td>
              <td class="num">{{ formatMoney(item.price) }}</td>
              <td class="num">{{ item.quantity }}</td>
              <td class="num">{{ formatMoney(item.totalAmount) }}</td>
            </tr>
          </tbody>
        </table>

        <div class="detail-meta">
          收货信息：{{ detail.data.receiverName }} {{ detail.data.receiverPhone }} · {{ detail.data.receiverAddress }}
        </div>
        <div v-if="detail.data.remark" class="detail-meta">买家备注：{{ detail.data.remark }}</div>

        <div class="timeline">
          <div v-for="step in timeline" :key="step.text" class="tl-item" :class="{ on: step.on }">
            <div class="tl-dot"></div>
            <div class="tl-txt">{{ step.text }}<small>{{ step.time || '—' }}</small></div>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="detail.visible = false">关闭</el-button>
        <el-button v-if="detail.data?.status === 1" type="primary" @click="ship(detail.data)">发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getOrderDetail, getOrderPage, shipOrder } from '../api'
import { ORDER_STATUS, ORDER_TABS } from '../utils/constants'
import { formatMoney } from '../utils/format'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const activeTab = ref('')
const query = reactive({ page: 1, size: 10, keyword: '' })

async function load() {
  loading.value = true
  try {
    const data = await getOrderPage({
      page: query.page,
      size: query.size,
      status: activeTab.value === '' ? undefined : activeTab.value,
      keyword: query.keyword || undefined
    })
    list.value = data.list || []
    total.value = data.total || 0
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function onPageChange(page) {
  query.page = page
  load()
}

function itemsSummary(items) {
  if (!items || !items.length) return '—'
  return items.map((item) => `${item.name} ×${item.quantity}`).join('、')
}

/* ==================== 详情 ==================== */
const detail = reactive({ visible: false, loading: false, orderNo: '', data: null })

async function openDetail(orderNo) {
  detail.visible = true
  detail.orderNo = orderNo
  detail.data = null
  detail.loading = true
  try {
    detail.data = await getOrderDetail(orderNo)
  } catch {
    /* 拦截器已提示 */
  } finally {
    detail.loading = false
  }
}

/** 时间线：创建 → 支付 → 发货 → 完成 */
const timeline = computed(() => {
  const d = detail.data
  if (!d) return []
  const status = d.status
  const payNode = status === 0
    ? { text: '等待支付', time: '', on: false }
    : status === 4
      ? { text: '用户取消订单', time: '', on: true }
      : status === 5
        ? { text: '超时未支付，系统自动关闭', time: '', on: true }
        : { text: '支付成功', time: d.payTime, on: true }
  return [
    { text: '创建订单', time: d.createTime, on: true },
    payNode,
    { text: '商家发货', time: '', on: status >= 2 },
    { text: '交易完成', time: '', on: status === 3 }
  ]
})

/* ==================== 发货 ==================== */
async function ship(order) {
  const orderNo = order.orderNo
  try {
    await ElMessageBox.confirm(`确定对订单 ${orderNo} 执行发货吗？`, '发货确认', {
      confirmButtonText: '发货',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await shipOrder(orderNo)
    ElMessage.success(`已发货：${orderNo}`)
    detail.visible = false
    load()
  } catch {
    /* 拦截器已提示（如非已支付状态 → 5002） */
  }
}

onMounted(load)
</script>

<style scoped>
.item-summary {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: var(--text-2);
}
.detail-tbl { width: 100%; border-collapse: collapse; font-size: 12px; margin-bottom: 14px; }
.detail-tbl th { background: #FAFAFB; text-align: left; color: var(--text-2); font-weight: 500; padding: 8px 12px; border-bottom: 1px solid var(--border); }
.detail-tbl td { padding: 8px 12px; border-bottom: 1px solid var(--border); }
.detail-meta { color: var(--text-2); font-size: 12px; margin-bottom: 8px; }
</style>
