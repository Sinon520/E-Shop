/** 千分位金额（最多两位小数，整数不显示小数位） */
export function formatAmount(value) {
  const n = Number(value ?? 0)
  if (Number.isNaN(n)) return '0'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}

/** 人民币展示：¥ 15,382 */
export function formatMoney(value) {
  return `¥ ${formatAmount(value)}`
}

/** 大数缩写：12,680 → 1.27万（看板统计卡用） */
export function formatCompact(value) {
  const n = Number(value ?? 0)
  if (Number.isNaN(n)) return '0'
  if (Math.abs(n) >= 100000000) return `${(n / 100000000).toFixed(2)}亿`
  if (Math.abs(n) >= 10000) return `${(n / 10000).toFixed(2).replace(/\.?0+$/, '')}万`
  return formatAmount(n)
}
