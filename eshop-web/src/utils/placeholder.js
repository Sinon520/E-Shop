/**
 * 商品占位图系统（与已确认原型一致）
 * 后端商品图 URL 为空，前端按「同色系渐变 + 简笔 SVG 图标」生成占位视觉
 */

const ICONS = {
  headphone: '<path d="M14 40 v-6 a18 18 0 0 1 36 0 v6"/><rect x="10" y="38" width="10" height="16" rx="4"/><rect x="44" y="38" width="10" height="16" rx="4"/>',
  overear: '<path d="M16 42 v-8 a16 16 0 0 1 32 0 v8"/><rect x="12" y="36" width="9" height="18" rx="4.5"/><rect x="43" y="36" width="9" height="18" rx="4.5"/>',
  speaker: '<rect x="16" y="8" width="32" height="48" rx="9"/><circle cx="32" cy="22" r="6"/><circle cx="32" cy="42" r="8"/>',
  watch: '<rect x="18" y="16" width="28" height="32" rx="9"/><path d="M24 16 v-6 h16 v6 M24 48 v6 h16 v-6"/><path d="M32 24 v6 h5"/>',
  keyboard: '<rect x="6" y="20" width="52" height="26" rx="6"/><circle cx="18" cy="29" r="1.6"/><circle cx="32" cy="29" r="1.6"/><circle cx="46" cy="29" r="1.6"/><path d="M22 39 h20"/>',
  mouse: '<rect x="20" y="8" width="24" height="48" rx="12"/><path d="M32 18 v8"/>',
  cup: '<path d="M22 20 h20 l-2.6 32 a6 6 0 0 1 -6 5.4 h-2.8 a6 6 0 0 1 -6 -5.4 z"/><path d="M20 12 h24 v6 H20z"/>',
  lamp: '<path d="M18 26 26 12 h12 l8 14 z"/><path d="M32 26 v22"/><path d="M22 52 h20"/>',
  bag: '<rect x="14" y="20" width="36" height="34" rx="10"/><path d="M23 20 v-5 a9 9 0 0 1 18 0 v5"/><path d="M26 34 h12"/>',
  shoe: '<path d="M8 46 c0 -9 7 -11 13 -15 l6 -8 h7 l3 9 c8 2 15 6 15 14 z"/><path d="M8 46 h44"/><path d="M27 31 l4 6 M36 28 l4 7"/>'
}

/** 商品 ID → 图标/色相（与原型 10 个商品一致；超出按 ID 取模兜底） */
const PRODUCT_STYLE = {
  1: { icon: 'headphone', hue: 210 },
  2: { icon: 'overear', hue: 265 },
  3: { icon: 'speaker', hue: 190 },
  4: { icon: 'watch', hue: 340 },
  5: { icon: 'keyboard', hue: 150 },
  6: { icon: 'mouse', hue: 25 },
  7: { icon: 'cup', hue: 200 },
  8: { icon: 'lamp', hue: 45 },
  9: { icon: 'bag', hue: 220 },
  10: { icon: 'shoe', hue: 10 }
}
const FALLBACK = Object.values(PRODUCT_STYLE)

/** 分类 ID → 图标/色相（原型 6 宫格；0 = 全部商品） */
const CATEGORY_STYLE = {
  1: { icon: 'headphone', hue: 210 },
  2: { icon: 'watch', hue: 340 },
  3: { icon: 'keyboard', hue: 150 },
  4: { icon: 'lamp', hue: 45 },
  5: { icon: 'bag', hue: 220 },
  0: { icon: 'overear', hue: 265 }
}

export function placeholderFor(id) {
  const style = PRODUCT_STYLE[id] || FALLBACK[(Number(id) || 0) % FALLBACK.length]
  return { icon: ICONS[style.icon], hue: style.hue }
}

export function categoryStyle(id) {
  const style = CATEGORY_STYLE[id] || CATEGORY_STYLE[0]
  return { icon: ICONS[style.icon], hue: style.hue }
}

export function imageBg(hue) {
  return `background:linear-gradient(135deg,hsl(${hue},72%,90%),hsl(${hue},78%,76%))`
}

export function imageStroke(hue) {
  return `hsl(${hue},42%,40%)`
}
