import request from './request'

/** 认证与用户（契约 §2） */
export const userApi = {
  register(data) {
    return request.post('/user/auth/register', data)
  },
  login(data) {
    return request.post('/user/auth/login', data)
  },
  profile() {
    return request.get('/user/profile')
  }
}

/** 收货地址（契约 §7） */
export const addressApi = {
  list() {
    return request.get('/user/addresses')
  },
  create(data) {
    return request.post('/user/addresses', data)
  },
  update(id, data) {
    return request.put(`/user/addresses/${id}`, data)
  },
  remove(id) {
    return request.delete(`/user/addresses/${id}`)
  }
}

/** 商品（契约 §3；P4 搜索走 ES + IK 分词，后端异常自动降级 DB） */
export const productApi = {
  categories() {
    return request.get('/product/categories')
  },
  spuPage(params) {
    return request.get('/product/spu/page', { params })
  },
  spuSearch(params) {
    return request.get('/product/spu/search', { params })
  },
  spuDetail(id) {
    return request.get(`/product/spu/${id}`)
  }
}

/** 库存（契约 §4） */
export const stockApi = {
  available(skuId) {
    return request.get(`/stock/${skuId}`)
  }
}

/** 购物车（契约 §5） */
export const cartApi = {
  list() {
    return request.get('/cart/list')
  },
  add(data) {
    return request.post('/cart/add', data)
  },
  updateQuantity(data) {
    return request.put('/cart/quantity', data)
  },
  updateChecked(data) {
    return request.put('/cart/checked', data)
  },
  checkAll(checked) {
    return request.put('/cart/checked-all', { checked })
  },
  remove(skuId) {
    return request.delete(`/cart/${skuId}`)
  },
  batchRemove(skuIds) {
    return request.post('/cart/batch-remove', { skuIds })
  }
}

/** 订单（契约 §8） */
export const orderApi = {
  create(data) {
    return request.post('/order/create', data)
  },
  page(params) {
    return request.get('/order/page', { params })
  },
  detail(orderNo) {
    return request.get(`/order/${orderNo}`)
  },
  cancel(orderNo) {
    return request.put(`/order/${orderNo}/cancel`)
  }
}

/** 支付（契约 §9） */
export const payApi = {
  create(orderNo) {
    return request.post('/pay/create', { orderNo })
  },
  callback(data) {
    return request.post('/pay/callback', data)
  }
}
