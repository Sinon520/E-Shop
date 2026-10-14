import { defineStore } from 'pinia'
import { userApi } from '../api'

const TOKEN_KEY = 'eshop_token'
const USER_KEY = 'eshop_user'

/** 认证状态：token + 用户信息（持久化到 localStorage） */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    displayName: (s) => s.user?.nickname || s.user?.username || ''
  },
  actions: {
    async login(username, password) {
      const data = await userApi.login({ username, password })
      this.token = data.token
      this.user = data.user
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data.user))
      return data
    },
    async register(form) {
      await userApi.register(form)
    },
    logout() {
      this.clear()
    },
    clear() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
