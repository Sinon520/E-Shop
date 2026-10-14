import { defineStore } from 'pinia'
import { useAuthStore } from './auth'

/**
 * 全局 UI 状态：登录弹窗 + 「需要登录」闸门
 * requireLogin() 返回 Promise<boolean>：
 * - 已登录 → 直接 resolve(true)
 * - 未登录 → 弹登录框，登录成功 resolve(true)，取消 resolve(false)
 */
export const useUiStore = defineStore('ui', {
  state: () => ({
    loginVisible: false,
    _resolver: null
  }),
  actions: {
    openLogin() {
      this.loginVisible = true
    },
    resolveLogin(ok) {
      this.loginVisible = false
      if (this._resolver) {
        this._resolver(ok)
        this._resolver = null
      }
    },
    requireLogin() {
      const auth = useAuthStore()
      if (auth.isLoggedIn) {
        return Promise.resolve(true)
      }
      return new Promise((resolve) => {
        this._resolver = resolve
        this.loginVisible = true
      })
    }
  }
})
