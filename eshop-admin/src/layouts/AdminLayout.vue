<template>
  <div class="layout">
    <!-- ==================== 深色侧栏 ==================== -->
    <aside class="side">
      <div class="side-logo"><div class="mark">E</div>E-Shop<small>管理台</small></div>

      <el-menu class="side-menu" :default-active="route.path" router>
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon><span>数据看板</span>
        </el-menu-item>
        <el-menu-item index="/goods">
          <el-icon><Box /></el-icon><span>商品管理</span>
        </el-menu-item>
        <el-menu-item index="/category">
          <el-icon><Grid /></el-icon><span>分类管理</span>
        </el-menu-item>
        <el-menu-item index="/stock">
          <el-icon><Coin /></el-icon><span>库存管理</span>
        </el-menu-item>
        <el-menu-item index="/orders">
          <el-icon><Tickets /></el-icon><span>订单管理</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon><span>用户管理</span>
        </el-menu-item>
      </el-menu>

      <div class="side-foot">架构：Spring Cloud 微服务<br />v1.0 · 运营管理端</div>
    </aside>

    <!-- ==================== 主区域 ==================== -->
    <div class="main">
      <header class="topbar">
        <h1>{{ route.meta.title }}</h1>
        <span class="crumb">{{ route.meta.crumb }}</span>
        <div class="spacer"></div>
        <div class="admin-chip">
          <div class="avatar">{{ (auth.name || 'A').charAt(0).toUpperCase() }}</div>
          {{ auth.name || 'admin' }}
        </div>
        <button class="logout-btn" @click="handleLogout">退出登录</button>
      </header>

      <div class="content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataBoard, Box, Grid, Coin, Tickets, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '退出确认', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  auth.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.layout { display: flex; min-height: 100vh; }

/* ---------- 侧栏 ---------- */
.side {
  width: 224px; flex: none; background: var(--side); color: #C3C8D4;
  display: flex; flex-direction: column; position: sticky; top: 0; height: 100vh;
}
.side-logo {
  display: flex; align-items: center; gap: 10px; height: 56px; padding: 0 18px;
  color: #fff; font-size: 16px; font-weight: 700; border-bottom: 1px solid rgba(255, 255, 255, .08);
}
.side-logo .mark {
  width: 28px; height: 28px; border-radius: 7px;
  background: linear-gradient(135deg, #FF6B3D, #EE4D2D);
  display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 800;
}
.side-logo small { font-size: 11px; color: #8A92A6; font-weight: 400; margin-left: 2px; }

.side-menu {
  flex: 1; padding: 12px 10px; border-right: none;
  --el-menu-bg-color: var(--side);
  --el-menu-text-color: #C3C8D4;
  --el-menu-active-color: #fff;
  --el-menu-hover-bg-color: rgba(255, 255, 255, .06);
  --el-menu-item-height: 42px;
}
.side-menu :deep(.el-menu-item) {
  border-radius: 9px; margin-bottom: 4px; padding: 0 14px; font-size: 14px;
}
.side-menu :deep(.el-menu-item .el-icon) { font-size: 17px; }
.side-menu :deep(.el-menu-item.is-active) {
  background: var(--side-active); font-weight: 500; box-shadow: inset 3px 0 0 var(--primary);
}
.side-foot { padding: 14px 18px; border-top: 1px solid rgba(255, 255, 255, .08); font-size: 12px; color: #8A92A6; }

/* ---------- 顶栏 ---------- */
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.topbar {
  height: 56px; background: #fff; border-bottom: 1px solid var(--border);
  display: flex; align-items: center; padding: 0 24px; gap: 14px;
  position: sticky; top: 0; z-index: 20;
}
.topbar h1 { font-size: 16px; font-weight: 600; }
.topbar .crumb { color: var(--text-3); font-size: 12px; margin-left: 2px; }
.topbar .spacer { flex: 1; }
.admin-chip { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text-2); }
.avatar {
  width: 30px; height: 30px; border-radius: 50%; background: var(--primary-light); color: var(--primary);
  display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 13px;
}
.logout-btn {
  color: var(--text-3); font-size: 13px; padding: 4px 8px; border-radius: 6px;
  border: none; background: none; cursor: pointer; transition: .15s;
}
.logout-btn:hover { color: var(--red); background: var(--red-light); }

.content { padding: 20px 24px 40px; flex: 1; }
</style>
