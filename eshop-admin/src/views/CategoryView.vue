<template>
  <div class="panel">
    <div class="toolbar">
      <div class="spacer"></div>
      <el-button type="primary" @click="openCreate">＋ 新增分类</el-button>
    </div>

    <el-table :data="list" v-loading="loading" row-key="id">
      <el-table-column label="ID" width="76" prop="id" />
      <el-table-column label="分类名称" min-width="180" prop="name" />
      <el-table-column label="层级" width="100">
        <template #default="{ row }">
          <el-tag :type="row.level === 1 ? 'primary' : 'info'">{{ row.level === 1 ? '一级' : '二级' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="父级" width="160">
        <template #default="{ row }">{{ parentName(row) }}</template>
      </el-table-column>
      <el-table-column label="排序" width="90" align="right">
        <template #default="{ row }"><span class="num">{{ row.sort ?? 0 }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.status === 1" type="success">启用</el-tag>
          <el-tag v-else type="info">停用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- ==================== 新增 / 编辑弹窗 ==================== -->
    <el-dialog v-model="dialog.visible" :title="dialog.id ? `编辑分类 #${dialog.id}` : '新增分类'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" placeholder="如：手机数码" />
        </el-form-item>

        <el-form-item label="层级" prop="level">
          <el-radio-group v-model="form.level" @change="onLevelChange">
            <el-radio-button :value="1">一级分类</el-radio-button>
            <el-radio-button :value="2">二级分类</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="form.level === 2" label="父级分类" prop="parentId">
          <el-select v-model="form.parentId" placeholder="请选择一级分类" style="width: 100%">
            <el-option v-for="item in level1Options" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="排序（越小越靠前）">
              <el-input-number v-model="form.sort" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createCategory, deleteCategory, getCategoryList, updateCategory } from '../api'

const loading = ref(false)
const list = ref([])

const level1Options = computed(() => list.value.filter((c) => c.level === 1))

function parentName(row) {
  if (!row.parentId) return '—'
  const parent = list.value.find((c) => c.id === row.parentId)
  return parent ? parent.name : `#${row.parentId}`
}

async function load() {
  loading.value = true
  try {
    const data = await getCategoryList()
    list.value = createOrder(data || [])
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

/** 一级在上、其二级紧跟其后（原型展示顺序） */
function createOrder(cats) {
  const level1 = cats.filter((c) => c.level === 1).sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || a.id - b.id)
  const level2 = cats.filter((c) => c.level !== 1).sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || a.id - b.id)
  const result = []
  level1.forEach((parent) => {
    result.push(parent)
    result.push(...level2.filter((c) => c.parentId === parent.id))
  })
  const orphans = level2.filter((c) => !level1.some((p) => p.id === c.parentId))
  result.push(...orphans)
  return result
}

/* ==================== 新增 / 编辑 ==================== */
const formRef = ref(null)
const saving = ref(false)
const dialog = reactive({ visible: false, id: null })
const form = reactive({ name: '', level: 1, parentId: 0, sort: 1, status: 1 })

const formRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  parentId: [
    {
      validator: (rule, value, callback) => {
        if (form.level === 2 && !value) callback(new Error('请选择父级分类'))
        else callback()
      },
      trigger: 'change'
    }
  ]
}

function onLevelChange(level) {
  if (level === 1) form.parentId = 0
  else form.parentId = null
}

function openCreate() {
  dialog.id = null
  Object.assign(form, { name: '', level: 1, parentId: 0, sort: 1, status: 1 })
  dialog.visible = true
}

function openEdit(row) {
  dialog.id = row.id
  Object.assign(form, {
    name: row.name,
    level: row.level ?? 1,
    parentId: row.level === 2 ? row.parentId ?? null : 0,
    sort: row.sort ?? 0,
    status: row.status ?? 1
  })
  dialog.visible = true
}

async function save() {
  if (saving.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const payload = {
    name: form.name.trim(),
    level: form.level,
    parentId: form.level === 1 ? 0 : form.parentId,
    sort: form.sort ?? 0,
    status: form.status
  }

  saving.value = true
  try {
    if (dialog.id) {
      await updateCategory(dialog.id, payload)
      ElMessage.success('分类已更新')
    } else {
      await createCategory(payload)
      ElMessage.success('分类创建成功')
    }
    dialog.visible = false
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

/* ==================== 删除（有子分类 / 商品引用时后端拒绝） ==================== */
async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？有商品引用时将被拒绝。`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteCategory(row.id)
    ElMessage.success(`已删除：${row.name}`)
    load()
  } catch {
    /* 拦截器已提示（400 引用保护） */
  }
}

onMounted(load)
</script>
