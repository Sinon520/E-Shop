/** 订单状态字典（与后端 OrderStatus 一致） */
export const ORDER_STATUS = {
  0: { text: '待支付', color: '#F2A93B', tag: 'warning' },
  1: { text: '已支付', color: '#3B82F6', tag: 'primary' },
  2: { text: '已发货', color: '#1F9D55', tag: 'success' },
  3: { text: '已完成', color: '#0EA5A5', tag: 'success' },
  4: { text: '已取消', color: '#B8BFC9', tag: 'info' },
  5: { text: '超时关闭', color: '#8A92A6', tag: 'info' }
}

export const ORDER_TABS = [
  { label: '全部', value: '' },
  { label: '待支付', value: '0' },
  { label: '已支付', value: '1' },
  { label: '已发货', value: '2' },
  { label: '已完成', value: '3' },
  { label: '已取消', value: '4' },
  { label: '已超时关闭', value: '5' }
]
