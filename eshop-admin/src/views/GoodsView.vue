<template>
  <div class="panel">
    <!-- ==================== 工具条 ==================== -->
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="商品名称 / ID"
        style="width: 200px"
        clearable
        @keyup.enter="search"
        @clear="search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>

      <el-select v-model="query.categoryId" placeholder="全部分类" style="width: 160px" clearable @change="search">
        <el-option v-for="item in categoryOptions" :key="item.id" :label="item.label" :value="item.id" />
      </el-select>

      <el-select v-model="query.status" placeholder="全部状态" style="width: 130px" clearable @change="search">
        <el-option label="上架中" :value="1" />
        <el-option label="已下架" :value="0" />
      </el-select>

      <el-button @click="reset">重置</el-button>
      <div class="spacer"></div>
      <el-button type="primary" @click="openCreate">＋ 新增商品</el-button>
    </div>

    <!-- ==================== 商品表格（expand 展开 SKU） ==================== -->
    <el-table
      :data="list"
      v-loading="loading"
      row-key="id"
      @expand-change="onExpand"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="sku-panel">
            <div v-if="skuLoading[row.id]" style="padding: 10px 14px; color: var(--text-3); font-size: 12px">SKU 加载中…</div>
            <table v-else-if="skuCache[row.id]?.length" class="sku-tbl">
              <thead>
                <tr><th>SKU ID</th><th>规格</th><th class="num">售价</th><th class="num">市场价</th><th>状态</th></tr>
              </thead>
              <tbody>
                <tr v-for="sku in skuCache[row.id]" :key="sku.id">
                  <td class="num">{{ sku.id }}</td>
                  <td>{{ sku.name }}</td>
                  <td class="num">{{ formatMoney(sku.price) }}</td>
                  <td class="num" style="color: var(--text-3)">{{ sku.marketPrice ? formatMoney(sku.marketPrice) : '—' }}</td>
                  <td>
                    <el-tag :type="sku.status === 1 ? 'success' : 'info'" size="small">
                      {{ sku.status === 1 ? '启用' : '停用' }}
                    </el-tag>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-else style="padding: 10px 14px; color: var(--text-3); font-size: 12px">该商品暂无 SKU</div>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="ID" width="76" prop="id" />

      <el-table-column label="商品" min-width="280">
        <template #default="{ row }">
          <div class="cell-main">
            <div class="thumb-img" :style="{ background: row.mainImage ? 'transparent' : thumbBg(row.id) }">
              <img v-if="row.mainImage" :src="row.mainImage" alt="" />
              <template v-else>{{ (row.name || '?').charAt(0) }}</template>
            </div>
            <div>
              <div class="name">{{ row.name }}</div>
              <div class="desc">{{ row.skuCount ?? 0 }} 个 SKU</div>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="分类" width="120" prop="categoryName" />

      <el-table-column label="价格" width="120" align="right">
        <template #default="{ row }">
          <span class="num">{{ row.price != null ? formatMoney(row.price) : '—' }}</span>
        </template>
      </el-table-column>

      <el-table-column label="销量" width="90" align="right">
        <template #default="{ row }"><span class="num">{{ row.sales ?? 0 }}</span></template>
      </el-table-column>

      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.status === 1" type="success">上架中</el-tag>
          <el-tag v-else type="info">已下架</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status === 1" link type="danger" @click="toggleStatus(row, 0)">下架</el-button>
          <el-button v-else link type="primary" @click="toggleStatus(row, 1)">上架</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- ==================== 分页 ==================== -->
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

    <!-- ==================== 新增 / 编辑弹窗 ==================== -->
    <el-dialog v-model="dialog.visible" :title="dialog.id ? `编辑商品 #${dialog.id}` : '新增商品'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入 SPU 名称" />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属分类" prop="categoryId">
              <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
                <el-option v-for="item in categoryOptions" :key="item.id" :label="item.label" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌 ID（选填）">
              <el-input-number v-model="form.brandId" :min="0" :controls="false" placeholder="0 = 无品牌" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="主图 URL">
          <el-input v-model="form.mainImage" placeholder="图片 URL（留空则使用占位图）" />
        </el-form-item>

        <!-- 新增：初始 SKU 列表 -->
        <el-form-item v-if="!dialog.id" label="初始 SKU（至少 1 条）">
          <div class="sku-edit">
            <div class="sku-edit-row" v-for="(sku, index) in form.skus" :key="index">
              <el-input v-model="sku.name" placeholder="规格名（如：42 号 / 曜石黑 256G）" />
              <el-input-number v-model="sku.price" :min="0.01" :precision="2" :controls="false" placeholder="售价" style="width: 130px" />
              <el-input-number v-model="sku.marketPrice" :min="0" :precision="2" :controls="false" placeholder="市场价" style="width: 130px" />
              <el-button link type="danger" :disabled="form.skus.length <= 1" @click="removeSku(index)">删除</el-button>
            </div>
            <el-button size="small" style="margin-top: 8px" @click="addSku">＋ 添加 SKU</el-button>
          </div>
        </el-form-item>

        <!-- 编辑：SKU 只读展示 -->
        <el-form-item v-else label="SKU 规格">
          <div class="sku-readonly">
            <template v-if="editSkus.length">
              <span v-for="sku in editSkus" :key="sku.id" class="sku-chip">{{ sku.name }} · {{ formatMoney(sku.price) }}</span>
            </template>
            <span v-else style="color: var(--text-3); font-size: 12px">暂无 SKU</span>
            <div style="color: var(--text-3); font-size: 12px; margin-top: 6px">编辑暂不支持修改 SKU 结构（可在库存管理中调整数量）</div>
          </div>
        </el-form-item>

        <el-form-item label="商品详情">
          <el-input v-model="form.detail" type="textarea" :rows="3" placeholder="商品详情（富文本占位）" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { createSpu, getCategoryList, getSpuDetail, getSpuPage, updateSpu, updateSpuStatus } from '../api'
import { formatMoney } from '../utils/format'

const THUMB_COLORS = ['#EE4D2D', '#1B66D6', '#1F9D55', '#D97614', '#7C4DFF', '#0EA5A5']
const thumbBg = (id) => THUMB_COLORS[Number(id || 0) % THUMB_COLORS.length]

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, keyword: '', categoryId: null, status: null })

const categoryOptions = ref([])
const skuCache = reactive({})
const skuLoading = reactive({})

/* ==================== 列表 ==================== */
async function load() {
  loading.value = true
  try {
    const data = await getSpuPage({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      categoryId: query.categoryId ?? undefined,
      status: query.status ?? undefined
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

function reset() {
  query.keyword = ''
  query.categoryId = null
  query.status = null
  search()
}

function onPageChange(page) {
  query.page = page
  load()
}

/* ==================== SKU 展开（懒加载管理端详情） ==================== */
async function onExpand(row, expandedRows) {
  const expanded = expandedRows && expandedRows.some((item) => item.id === row.id)
  if (!expanded || skuCache[row.id]) return
  skuLoading[row.id] = true
  try {
    const detail = await getSpuDetail(row.id)
    skuCache[row.id] = detail?.skus || []
  } catch {
    skuCache[row.id] = []
  } finally {
    skuLoading[row.id] = false
  }
}

/* ==================== 分类选项 ==================== */
async function loadCategories() {
  try {
    const cats = await getCategoryList()
    categoryOptions.value = (cats || [])
      .sort((a, b) => (a.parentId - b.parentId) || ((a.sort ?? 0) - (b.sort ?? 0)) || (a.id - b.id))
      .map((c) => ({ id: c.id, label: c.level === 2 ? `　└ ${c.name}` : c.name }))
  } catch {
    /* 拦截器已提示 */
  }
}

/* ==================== 上下架 ==================== */
async function toggleStatus(row, status) {
  const action = status === 1 ? '上架' : '下架'
  try {
    await ElMessageBox.confirm(
      `确定${action}「${row.name}」吗？${status === 0 ? '下架后 C 端将不可见（缓存同步失效）。' : ''}`,
      `${action}确认`,
      { confirmButtonText: action, cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await updateSpuStatus(row.id, status)
    ElMessage.success(`已${action}：${row.name}`)
    load()
  } catch {
    /* 拦截器已提示 */
  }
}

/* ==================== 新增 / 编辑 ==================== */
const formRef = ref(null)
const saving = ref(false)
const editSkus = ref([])
const dialog = reactive({ visible: false, id: null })
const form = reactive({ name: '', categoryId: null, brandId: 0, mainImage: '', detail: '', skus: [] })

const formRules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }]
}

function openCreate() {
  dialog.id = null
  editSkus.value = []
  Object.assign(form, { name: '', categoryId: null, brandId: 0, mainImage: '', detail: '', skus: [newSku()] })
  dialog.visible = true
}

async function openEdit(row) {
  dialog.id = row.id
  editSkus.value = []
  Object.assign(form, {
    name: row.name,
    categoryId: row.categoryId ?? null,
    brandId: 0,
    mainImage: row.mainImage || '',
    detail: '',
    skus: []
  })
  dialog.visible = true
  try {
    const detail = await getSpuDetail(row.id)
    if (dialog.id === row.id) {
      form.detail = detail?.detail || ''
      editSkus.value = detail?.skus || []
    }
  } catch {
    /* 拦截器已提示 */
  }
}

function newSku() {
  return { name: '', price: null, marketPrice: null }
}

function addSku() {
  form.skus.push(newSku())
}

function removeSku(index) {
  form.skus.splice(index, 1)
}

async function save() {
  if (saving.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const payload = {
    name: form.name.trim(),
    categoryId: form.categoryId,
    brandId: form.brandId || 0,
    mainImage: form.mainImage || '',
    detail: form.detail || ''
  }

  if (!dialog.id) {
    const invalidSku = form.skus.find((sku) => !sku.name?.trim() || sku.price == null)
    if (invalidSku) {
      ElMessage.warning('请完整填写每条 SKU 的规格名与售价')
      return
    }
    payload.skus = form.skus.map((sku) => ({
      name: sku.name.trim(),
      price: sku.price,
      marketPrice: sku.marketPrice || null
    }))
  }

  saving.value = true
  try {
    if (dialog.id) {
      await updateSpu(dialog.id, payload)
      ElMessage.success('商品已更新')
    } else {
      await createSpu(payload)
      ElMessage.success('商品创建成功')
    }
    dialog.visible = false
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadCategories()
  load()
})
</script>

<style scoped>
.sku-panel { background: #FAFAFB; padding: 10px 14px; }
.sku-tbl { width: 100%; border-collapse: collapse; font-size: 12px; }
.sku-tbl th { text-align: left; color: var(--text-2); font-weight: 500; padding: 6px 10px; border-bottom: 1px solid var(--border); }
.sku-tbl td { padding: 6px 10px; border-bottom: 1px solid var(--border); }
.sku-tbl tr:last-child td { border-bottom: none; }

.sku-edit-row { display: flex; gap: 10px; align-items: center; margin-bottom: 8px; }
.sku-edit-row :deep(.el-input:first-child) { flex: 1; }
.sku-readonly { display: flex; flex-wrap: wrap; gap: 8px; }
.sku-chip {
  background: var(--primary-light); color: var(--primary);
  border-radius: 6px; padding: 2px 10px; font-size: 12px;
}
</style>
