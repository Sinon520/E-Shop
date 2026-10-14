<template>
  <div class="mask" :class="{ show: ui.loginVisible }" @click="cancel">
    <div class="modal" @click.stop>
      <span class="modal-close" @click="cancel">✕</span>
      <div class="tabs2">
        <span :class="{ on: mode === 'login' }" @click="switchMode('login')">登录</span>
        <span :class="{ on: mode === 'reg' }" @click="switchMode('reg')">注册</span>
      </div>
      <div class="fld"><label>用户名</label><input v-model.trim="form.username" placeholder="4-20 位字母、数字或下划线" /></div>
      <div class="fld"><label>密码</label><input v-model.trim="form.password" type="password" placeholder="6-20 位" @keydown.enter="submit" /></div>
      <div class="fld" v-if="mode === 'reg'"><label>手机号（选填）</label><input v-model.trim="form.phone" placeholder="11 位手机号" /></div>
      <button class="btn btn-primary" style="width:100%" :disabled="loading" @click="submit">
        {{ loading ? '请稍候…' : mode === 'login' ? '登 录' : '注 册' }}
      </button>
      <p class="modal-tip">{{ mode === 'login' ? '演示账号：sinon / 123456' : '注册成功后自动登录（对应接口 2.1 / 2.2）' }}</p>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useCartStore } from '../stores/cart'
import { useUiStore } from '../stores/ui'
import { toast } from '../utils/toast'

const auth = useAuthStore()
const cart = useCartStore()
const ui = useUiStore()

const mode = ref('login')
const loading = ref(false)
const form = reactive({ username: '', password: '', phone: '' })

watch(
  () => ui.loginVisible,
  (visible) => {
    if (visible) {
      mode.value = 'login'
      loading.value = false
      form.password = ''
    }
  }
)

function switchMode(m) {
  mode.value = m
}

async function submit() {
  if (loading.value) return
  if (!form.username || !form.password) return toast('请输入用户名和密码')
  if (mode.value === 'reg' && !/^[a-zA-Z0-9_]{4,20}$/.test(form.username)) {
    return toast('用户名须为 4-20 位字母、数字或下划线')
  }
  if (form.password.length < 6 || form.password.length > 20) return toast('密码长度须为 6-20 位')
  if (mode.value === 'reg' && form.phone && !/^1\d{10}$/.test(form.phone)) return toast('手机号格式不正确')

  loading.value = true
  try {
    if (mode.value === 'reg') {
      await auth.register({
        username: form.username,
        password: form.password,
        phone: form.phone || undefined
      })
      await auth.login(form.username, form.password)
      toast('注册成功，已自动登录')
    } else {
      await auth.login(form.username, form.password)
      toast('欢迎回来，' + auth.displayName)
    }
    cart.fetch().catch(() => {})
    ui.resolveLogin(true)
  } catch (e) {
    /* 业务错误已由拦截器 toast（1001/1002/1003 等） */
  } finally {
    loading.value = false
  }
}

function cancel() {
  ui.resolveLogin(false)
}
</script>
