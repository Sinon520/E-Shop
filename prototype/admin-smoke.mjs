// =====================================================================
// 管理后台原型静态冒烟：JS 语法 + id 引用一致性 + onclick 函数存在性
// 用法: node prototype/admin-smoke.mjs
// =====================================================================
import { readFileSync } from 'node:fs';

const html = readFileSync(new URL('./admin.html', import.meta.url), 'utf-8');
let pass = 0, fail = 0;
function check(name, ok, detail = '') {
  if (ok) { pass++; console.log('[PASS] ' + name); }
  else { fail++; console.log('[FAIL] ' + name + (detail ? '  => ' + detail : '')); }
}

// 1. 提取 script
const m = html.match(/<script>([\s\S]*)<\/script>/);
check('存在 <script> 块', !!m);
const js = m ? m[1] : '';

// 2. JS 语法可解析
try {
  new Function(js);
  check('JS 语法可解析', true);
} catch (e) {
  check('JS 语法可解析', false, e.message);
}

// 3. $('#id') 引用与 HTML id 一致性
const ids = new Set([...html.matchAll(/id="([^"]+)"/g)].map(x => x[1]));
const refs = [...new Set([...js.matchAll(/\$\('#([^']+)'\)/g)].map(x => x[1]))];
const missing = refs.filter(r => !ids.has(r));
check(`$('#id') 引用全部存在（共 ${refs.length} 个）`, missing.length === 0, missing.join(','));

// 4. onclick 引用的函数均已定义（含 HTML 与 JS 字符串两处）
const defined = new Set([...js.matchAll(/function\s+([A-Za-z_$][\w$]*)\s*\(/g)].map(x => x[1]));
const calls = new Set([
  ...[...html.matchAll(/onclick="([A-Za-z_$][\w$]*)\(/g)].map(x => x[1]),
  ...[...js.matchAll(/onclick="([A-Za-z_$][\w$]*)\(/g)].map(x => x[1])
]);
const RESERVED = new Set(['if', 'for', 'while', 'return', 'window']);
const undef = [...calls].filter(c => !defined.has(c) && !RESERVED.has(c));
check(`onclick 函数均已定义（共 ${calls.size} 个，引用 ${[...calls].join('/')}）`, undef.length === 0, undef.join(','));

// 5. 关键元素与页面 section 存在
const required = ['loginView', 'appView', 'menu', 'statCards', 'trendChart', 'statusChart', 'rankList', 'todoList',
  'goodsBody', 'catBody', 'stockBody', 'orderBody', 'userBody', 'modalRoot', 'toast',
  'page-dashboard', 'page-goods', 'page-category', 'page-stock', 'page-orders', 'page-users'];
const missEls = required.filter(id => !ids.has(id));
check('关键元素/页面 section 齐备', missEls.length === 0, missEls.join(','));
check('页面 section 数量 = 6', html.match(/id="page-/g)?.length === 6);

// 6. Mock 数据完整性
check('SPU 数据 10 条', (js.match(/id:\d+,name:'/g) || []).length >= 10);
check('订单状态覆盖 0~5', [0, 1, 2, 3, 4, 5].every(s => js.includes('status:' + s + ',')));

console.log(`\n==== ADMIN PROTOTYPE SMOKE: PASS ${pass} / FAIL ${fail} ====`);
process.exit(fail ? 1 : 0);
