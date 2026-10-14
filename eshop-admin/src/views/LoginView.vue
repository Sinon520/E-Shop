<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="login-logo"><div class="mark">E</div>E-Shop 管理后台</div>
      <div class="login-sub">微服务电商平台 · 运营管理端</div>

      <el-form ref="formRef" :model="form" :rules="rules" @keyup.enter="doLogin">
        <el-form-item prop="username" label="管理员账号">
          <el-input v-model="form.username" size="large" placeholder="请输入管理员账号" />
        </el-form-item>
        <el-form-item prop="password" label="密码">
          <el-input v-model="form.password" size="large" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
      </el-form>

      <button class="login-btn" :disabled="loading" @click="doLogin">
        {{ loading ? '登录中…' : '登 录' }}
      </button>
      <div class="login-tip">演示账号 admin / admin123</div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function doLogin() {
  if (loading.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    ElMessage.success(`登录成功，欢迎回来 ${auth.name}`)
    router.push(route.query.redirect || '/dashboard')
  } catch (e) {
    /* 错误提示已由拦截器统一处理 */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1F2430, #2A3040 60%, #3A2A26);
}
.login-card {
  width: 400px;
  background: #fff;
  border-radius: 16px;
  padding: 40px 36px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, .35);
}
.login-logo { display: flex; align-items: center; gap: 10px; font-size: 20px; font-weight: 700; margin-bottom: 6px; }
.login-logo .mark {
  width: 36px; height: 36px; border-radius: 9px;
  background: linear-gradient(135deg, #FF6B3D, #EE4D2D);
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 18px; font-weight: 800;
}
.login-sub { color: var(--text-3); font-size: 13px; margin-bottom: 26px; }
:deep(.el-form-item) { margin-bottom: 16px; }
:deep(.el-form-item__label) { display: block; float: none; margin-bottom: 6px; line-height: 1.6; padding: 0; }
:deep(.el-form-item__content) { display: block; }
:deep(.el-input__wrapper) { border-radius: 8px; }
:deep(.el-input__inner) { height: 40px; }
.login-btn {
  width: 100%; height: 42px; border-radius: 8px; border: none; cursor: pointer;
  background: var(--primary); color: #fff; font-size: 15px; font-weight: 600; margin-top: 8px;
  transition: .15s;
}
.login-btn:hover { background: var(--primary-dark); }
.login-btn:disabled { opacity: .7; cursor: not-allowed; }
.login-tip { margin-top: 16px; text-align: center; color: var(--text-3); font-size: 12px; }
</style>
