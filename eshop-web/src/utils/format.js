/**
 * 数值格式化：金额 / 销量等（后端金额为 number，单位元）
 */
export function fmt(value) {
  const n = Number(value ?? 0)
  if (Number.isNaN(n)) return '0'
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}
