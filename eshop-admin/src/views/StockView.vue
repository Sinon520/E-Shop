<template>
  <div class="panel">
    <!-- ==================== 工具条 ==================== -->
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="SKU ID / 商品名称"
        style="width: 200px"
        clearable
        @keyup.enter="search"
        @clear="search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>

      <el-checkbox v-model="query.lowStock" @change="search">仅看低库存（&lt; 20）</el-checkbox>

      <div class="spacer"></div>
      <el-button :loading="preheating" @click="handlePreheat">缓存预热</el-button>
    </div>

    <!-- ==================== 库存表格 ==================== -->
    <el-table :data="list" v-loading="loading" row-key="skuId">
      <el-table-column label="SKU ID" width="90" prop="skuId" />
      <el-table-column label="商品 / 规格" min-width="260">
        <template #default="{ row }">
          <div class="cell-main">
            <div class="thumb-img" :style="{ background: thumbBg(row.skuId) }">{{ (row.spuName || '?').charAt(0) }}</div>
            <div>
              <div class="name">{{ row.spuName }}</div>
              <div class="desc">{{ row.skuName }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="可售" width="90" align="right">
        <template #default="{ row }">
          <span class="num" :class="{ 'low-stock': row.available < LOW_STOCK }">{{ row.available }}</span>
        </template>
      </el-table-column>
      <el-table-column label="锁定" width="80" align="right">
        <template #default="{ row }"><span class="num">{{ row.locked }}</span></template>
      </el-table-column>
      <el-table-column label="已售" width="80" align="right">
        <template #default="{ row }"><span class="num">{{ row.sold }}</span></template>
      </el-table-column>
      <el-table-column label="Redis 预扣值" min-width="180">
        <template #default="{ row }">
          <template v-if="row.redisValue == null">
            <el-tag type="info">未预热</el-tag>
          </template>
          <template v-else-if="row.consistent">
            <el-tag type="primary">一致 · {{ row.redisValue }}</el-tag>
          </template>
          <template v-else>
            <el-tag type="danger">不一致 · Redis {{ row.redisValue }} / DB {{ row.available }}</el-tag>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openAdjust(row)">调整库存</el-button>
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

    <!-- ==================== 调整库存弹窗 ==================== -->
    <el-dialog v-model="dialog.visible" :title="`调整库存 · SKU ${dialog.row?.skuId ?? ''}`" width="480px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="商品">
          <el-input :model-value="`${dialog.row?.spuName ?? ''} / ${dialog.row?.skuName ?? ''}`" disabled />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="当前可售">
              <el-input :model-value="String(dialog.row?.available ?? '')" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="调整方式">
              <el-select v-model="adjust.mode" style="width: 100%">
                <el-option label="增量调整（+/-）" value="delta" />
                <el-option label="设为绝对值" value="absolute" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="调整数量">
              <el-input-number v-model="adjust.value" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原因备注">
              <el-input v-model="adjust.remark" placeholder="如：补货入库" />
            </el-form-item>
          </el-col>
        </el-row>

        <div style="color: var(--text-3); font-size: 12px">
          提示：调整后同步刷新 Redis 预扣值（与 DB 保持一致）；增量模式可填负数。
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAdjust">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { adjustStock, getStockPage, preheatStock } from '../api'

const LOW_STOCK = 20
const THUMB_COLORS = ['#EE4D2D', '#1B66D6', '#1F9D55', '#D97614', '#7C4DFF', '#0EA5A5']
const thumbBg = (id) => THUMB_COLORS[Number(id || 0) % THUMB_COLORS.length]

const loading = ref(false)
const preheating = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, keyword: '', lowStock: false })

async function load() {
  loading.value = true
  try {
    const data = await getStockPage({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      lowStock: query.lowStock || undefined
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

/* ==================== 缓存预热 ==================== */
async function handlePreheat() {
  if (preheating.value) return
  preheating.value = true
  try {
    const result = await preheatStock()
    ElMessage.success(result || 'Redis 库存预热完成')
    load()
  } catch (e) {
    ElMessage.error(e?.message || '预热失败，请确认 stock 服务与 Redis 正常')
  } finally {
    preheating.value = false
  }
}

/* ==================== 调整库存 ==================== */
const dialog = reactive({ visible: false, row: null })
const saving = ref(false)
const adjust = reactive({ mode: 'delta', value: 10, remark: '' })

function openAdjust(row) {
  dialog.row = row
  adjust.mode = 'delta'
  adjust.value = 10
  adjust.remark = ''
  dialog.visible = true
}

async function saveAdjust() {
  if (saving.value) return
  if (adjust.value == null) {
    ElMessage.warning('请输入调整数量')
    return
  }
  saving.value = true
  try {
    await adjustStock(dialog.row.skuId, {
      mode: adjust.mode,
      value: adjust.value,
      remark: adjust.remark || ''
    })
    ElMessage.success(`库存已调整：SKU ${dialog.row.skuId}`)
    dialog.visible = false
    load()
  } catch {
    /* 拦截器已提示（如：调整后可售库存不能为负） */
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
