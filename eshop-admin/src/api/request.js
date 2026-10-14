import axios from 'axios'
import { ElMessage } from 'element-plus'

export const TOKEN_KEY = 'eshop_admin_token'

/**
 * 管理后台统一请求封装（对接网关 /api，开发态由 Vite 代理转发到 9000）
 * - 请求：注入 Bearer token（admin JWT，网关校验 role=admin）
 * - 响应：解包 {code,message,data}；code!==200 统一 ElMessage 提示并 reject
 * - 401：清登录态并跳转登录页（与后端约定的“管理员登录过期”信号）
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code === 200) {
        return res.data
      }
      if (res.code === 401 || response.status === 401) {
        handleUnauthorized()
      } else {
        ElMessage.error(res.message || '操作失败')
      }
      return Promise.reject(Object.assign(new Error(res.message || '操作失败'), { code: res.code }))
    }
    return res
  },
  (error) => {
    if (error.response?.status === 401) {
      handleUnauthorized()
    } else if (error.response?.status === 403) {
      ElMessage.error('无管理权限，请使用管理员账号登录')
    } else {
      ElMessage.error('网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

let redirecting = false
function handleUnauthorized() {
  if (redirecting) return
  redirecting = true
  localStorage.removeItem(TOKEN_KEY)
  ElMessage.warning('登录已过期，请重新登录')
  const target = `${window.location.pathname}${window.location.search}`
  setTimeout(() => {
    window.location.href = `/login?redirect=${encodeURIComponent(target)}`
    redirecting = false
  }, 300)
}

export default request
