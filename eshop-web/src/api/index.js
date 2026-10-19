import request, { TOKEN_KEY } from './request'

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

/**
 * AI 客服（P5）
 * 走网关 /api/ai/**，可选鉴权：带 token 时网关会注入身份，游客也能问政策类问题。
 * - chat：非流式，脚本与降级用
 * - chatStream：SSE 流式。EventSource 只支持 GET，所以用 fetch + ReadableStream 自己解析
 */
export const aiApi = {
  chat(data) {
    return request.post('/ai/chat', data, { timeout: 45000 })
  },

  async chatStream(data, { onMeta, onStatus, onDelta, onDone, onError, signal } = {}) {
    const token = localStorage.getItem(TOKEN_KEY)
    const resp = await fetch('/api/ai/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify(data),
      signal
    })

    if (!resp.ok || !resp.body) {
      throw new Error(`HTTP ${resp.status}`)
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    const dispatch = (event) => {
      switch (event.type) {
        case 'meta':
          onMeta?.(event)
          break
        case 'status':
          onStatus?.(event.text || '')
          break
        case 'delta':
          onDelta?.(event.text || '')
          break
        case 'done':
          onDone?.(event)
          break
        case 'error':
          onError?.(event.message || '客服暂时不可用')
          break
        default:
          break
      }
    }

    while (true) {
      const { value, done } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      let cut
      while ((cut = buffer.indexOf('\n\n')) >= 0) {
        const raw = buffer.slice(0, cut)
        buffer = buffer.slice(cut + 2)

        const line = raw.split('\n').find((l) => l.startsWith('data:'))
        if (!line) continue

        try {
          dispatch(JSON.parse(line.slice(5).trim()))
        } catch (e) {
          /* 半包或心跳，忽略 */
        }
      }
    }
  }
}
