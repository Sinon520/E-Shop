import axios from 'axios'
import { toast } from '../utils/toast'

export const TOKEN_KEY = 'eshop_token'

/**
 * 统一请求封装（对接网关 /api，开发态由 Vite 代理转发到 9000）
 * - 请求：live 读取 localStorage 注入 Bearer token
 * - 响应：解包 {code,message,data}；code!==200 统一 toast 并 reject（保留原始 code）
 * - 401（网关鉴权失败 / 登录过期）：广播事件，由 main.js 统一清登录态并弹登录框
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

function notifyUnauthorized() {
  window.dispatchEvent(new CustomEvent('eshop:unauthorized'))
}

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code === 200) {
        return res.data
      }
      if (res.code === 401) {
        notifyUnauthorized()
      } else {
        toast(res.message || '操作失败')
      }
      return Promise.reject(Object.assign(new Error(res.message || '操作失败'), { code: res.code }))
    }
    return res
  },
  (error) => {
    if (error.response?.status === 401) {
      notifyUnauthorized()
    } else {
      toast('网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default request
