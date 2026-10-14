import { defineStore } from 'pinia'
import { adminLogin } from '../api'
import { TOKEN_KEY } from '../api/request'

const NAME_KEY = 'eshop_admin_name'

/**
 * 管理员登录态（token 持久化 localStorage）
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    name: localStorage.getItem(NAME_KEY) || ''
  }),
  getters: {
    isLoggedIn: (state) => !!state.token
  },
  actions: {
    async login(username, password) {
      const data = await adminLogin({ username, password })
      this.token = data.token
      this.name = data.name || username
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(NAME_KEY, this.name)
      return data
    },
    logout() {
      this.token = ''
      this.name = ''
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(NAME_KEY)
    }
  }
})
