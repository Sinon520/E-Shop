import request from './request'

/* ==================== 管理员认证 ==================== */

/** 管理员登录 → { token, expiresIn, name } */
export function adminLogin(data) {
  return request.post('/admin/auth/login', data)
}

/* ==================== 数据看板 ==================== */

/** 看板总览：todaySales / todayOrders / pendingShip / totalSales / statusDist / trend7d / hotTop5 */
export function getDashboard() {
  return request.get('/admin/dashboard')
}

/* ==================== 商品管理 ==================== */

/** 商品分页（含下架）：{ page, size, keyword, categoryId, status } */
export function getSpuPage(params) {
  return request.get('/admin/product/spu/page', { params })
}

/** 上架 / 下架 */
export function updateSpuStatus(id, status) {
  return request.put(`/admin/product/spu/${id}/status`, { status })
}

/** 新增商品（含初始 SKU 列表） */
export function createSpu(data) {
  return request.post('/admin/product/spu', data)
}

/** 编辑商品 */
export function updateSpu(id, data) {
  return request.put(`/admin/product/spu/${id}`, data)
}

/** 商品详情（含 SKU 明细，不限状态；商品管理页 SKU 展开用） */
export function getSpuDetail(id) {
  return request.get(`/admin/product/spu/${id}`)
}

/* ==================== 分类管理 ==================== */

/** 全量分类（含停用） */
export function getCategoryList() {
  return request.get('/admin/product/category/list')
}

export function createCategory(data) {
  return request.post('/admin/product/category', data)
}

export function updateCategory(id, data) {
  return request.put(`/admin/product/category/${id}`, data)
}

export function deleteCategory(id) {
  return request.delete(`/admin/product/category/${id}`)
}

/* ==================== 库存管理 ==================== */

/** 库存分页（含 Redis 预扣值核对）：{ page, size, keyword, lowStock } */
export function getStockPage(params) {
  return request.get('/admin/stock/page', { params })
}

/** 调整库存：mode delta | absolute */
export function adjustStock(skuId, data) {
  return request.put(`/admin/stock/${skuId}/adjust`, data)
}

/** 缓存预热（运维操作，经 Vite 代理直连 stock 服务 /internal） */
export function preheatStock() {
  return request.post('/internal/stock/preheat', null, { baseURL: '' })
}

/* ==================== 订单管理 ==================== */

/** 订单分页（全量）：{ page, size, status, keyword } */
export function getOrderPage(params) {
  return request.get('/admin/order/page', { params })
}

/** 订单详情（不限归属） */
export function getOrderDetail(orderNo) {
  return request.get(`/admin/order/${orderNo}`)
}

/** 发货（已支付 → 已发货） */
export function shipOrder(orderNo) {
  return request.put(`/admin/order/${orderNo}/ship`)
}

/* ==================== 用户管理 ==================== */

/** 用户分页：{ page, size, keyword } */
export function getUserPage(params) {
  return request.get('/admin/user/page', { params })
}

/** 禁用 / 启用用户 */
export function updateUserStatus(id, status) {
  return request.put(`/admin/user/${id}/status`, { status })
}
