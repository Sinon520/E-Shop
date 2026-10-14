<template>
  <div class="panel">
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="用户名 / 手机号"
        style="width: 220px"
        clearable
        @keyup.enter="search"
        @clear="search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <div class="spacer"></div>
    </div>

    <el-table :data="list" v-loading="loading" row-key="id">
      <el-table-column label="ID" width="76" prop="id" />
      <el-table-column label="用户名" min-width="160">
        <template #default="{ row }">
          <b>{{ row.username }}</b>
          <span v-if="row.nickname && row.nickname !== row.username" class="desc" style="margin-left: 8px">
            {{ row.nickname }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="手机号" width="160">
        <template #default="{ row }"><span class="num">{{ row.phone || '—' }}</span></template>
      </el-table-column>
      <el-table-column label="注册时间" width="180">
        <template #default="{ row }"><span style="color: var(--text-2)">{{ row.createTime }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === 1" type="success">正常</el-tag>
          <el-tag v-else type="danger">已禁用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 1" link type="danger" @click="toggle(row, 0)">禁用</el-button>
          <el-button v-else link type="primary" @click="toggle(row, 1)">启用</el-button>
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getUserPage, updateUserStatus } from '../api'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, keyword: '' })

async function load() {
  loading.value = true
  try {
    const data = await getUserPage({
      page: query.page,
      size: query.size,
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

async function toggle(row, status) {
  const action = status === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定${action}用户「${row.username}」吗？`, `${action}确认`, {
      confirmButtonText: action,
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await updateUserStatus(row.id, status)
    ElMessage.success(`已${action}：${row.username}`)
    load()
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(load)
</script>
