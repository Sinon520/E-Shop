import { defineStore } from 'pinia'
import { cartApi } from '../api'

/** 购物车状态（数据源为服务端 Redis，本地仅缓存列表用于角标与页面渲染） */
export const useCartStore = defineStore('cart', {
  state: () => ({
    items: [],
    loaded: false
  }),
  getters: {
    /** 角标数 = 全部条目数量之和 */
    count: (s) => s.items.reduce((n, i) => n + (i.quantity || 0), 0),
    checkedItems: (s) => s.items.filter((i) => i.checked)
  },
  actions: {
    async fetch() {
      this.items = await cartApi.list()
      this.loaded = true
    },
    async add(skuId, quantity = 1) {
      await cartApi.add({ skuId, quantity })
      await this.fetch()
    },
    async updateQuantity(skuId, quantity) {
      await cartApi.updateQuantity({ skuId, quantity })
      await this.fetch()
    },
    async updateChecked(skuId, checked) {
      await cartApi.updateChecked({ skuId, checked })
      await this.fetch()
    },
    async checkAll(checked) {
      await cartApi.checkAll(checked)
      await this.fetch()
    },
    async remove(skuId) {
      await cartApi.remove(skuId)
      await this.fetch()
    },
    async batchRemove(skuIds) {
      await cartApi.batchRemove(skuIds)
      await this.fetch()
    },
    /** 退出登录 / 鉴权失效时清空本地缓存（服务端数据保留在 Redis） */
    clearLocal() {
      this.items = []
      this.loaded = false
    }
  }
})
