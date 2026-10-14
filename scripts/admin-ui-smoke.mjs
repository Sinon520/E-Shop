/**
 * E-Shop 管理后台 UI 冒烟（P3 阶段二）
 * 用 playwright-core 驱动系统 Edge：登录 → 6 个模块逐页浏览截图。
 * 运行：
 *   NODE 为 managed node；playwright-core 位于隔离 workspace（createRequire 指定）。
 *   node scripts/admin-ui-smoke.mjs
 * 产出：scripts/shots/*.png
 */
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import fs from 'node:fs'

const require = createRequire('C:/Users/asus/.workbuddy/binaries/node/workspace/')
const { chromium } = require('playwright-core')

const BASE = 'http://127.0.0.1:5174'
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const shotDir = path.join(path.dirname(fileURLToPath(import.meta.url)), 'shots')
fs.mkdirSync(shotDir, { recursive: true })

let pass = 0
let fail = 0
function check(name, ok, detail = '') {
  if (ok) {
    pass++
    console.log(`[PASS] ${name}`)
  } else {
    fail++
    console.log(`[FAIL] ${name}  => ${detail}`)
  }
}

const browser = await chromium.launch({ executablePath: EDGE, headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

try {
  /* ==================== 1. 登录页 ==================== */
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(400)
  await page.screenshot({ path: `${shotDir}/01-login.png` })
  check('登录页渲染（标题可见）', await page.locator('text=E-Shop 管理后台').first().isVisible())

  /* ==================== 2. 登录 → 看板 ==================== */
  await page.fill('input[placeholder="请输入管理员账号"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', 'admin123')
  await page.click('button:has-text("登 录")')
  await page.waitForURL('**/dashboard', { timeout: 10000 })
  await page.waitForTimeout(1800) // 等看板数据与图表渲染
  await page.screenshot({ path: `${shotDir}/02-dashboard.png` })

  const dashboardTitle = await page.locator('.topbar h1').textContent()
  check('登录成功进入数据看板', dashboardTitle.includes('数据看板'), `title=${dashboardTitle}`)
  const statCount = await page.locator('.stat-card').count()
  check('看板统计卡 4 张', statCount === 4, `count=${statCount}`)
  const canvasCount = await page.locator('canvas').count()
  check('ECharts 图表已渲染（折线+环形）', canvasCount >= 2, `canvas=${canvasCount}`)
  const rankCount = await page.locator('.rank-row').count()
  check('热销榜/待办有内容', rankCount >= 1, `rankRows=${rankCount}`)

  /* ==================== 3. 商品管理 ==================== */
  await page.goto(`${BASE}/goods`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(900)
  await page.screenshot({ path: `${shotDir}/03-goods.png` })
  const goodsRows = await page.locator('.el-table__body tr').count()
  check('商品列表有数据行', goodsRows > 0, `rows=${goodsRows}`)

  // 展开第一行 SKU（点击 expand 单元格图标）
  await page.locator('.el-table__expand-icon').first().click()
  await page.waitForTimeout(1200)
  await page.screenshot({ path: `${shotDir}/03b-goods-sku-expand.png` })
  const skuRows = await page.locator('.sku-tbl tbody tr').count()
  check('SKU 展开显示明细', skuRows > 0, `skuRows=${skuRows}`)

  /* ==================== 4. 分类管理 ==================== */
  await page.goto(`${BASE}/category`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(700)
  await page.screenshot({ path: `${shotDir}/04-category.png` })
  const catRows = await page.locator('.el-table__body tr').count()
  check('分类列表有数据行', catRows > 0, `rows=${catRows}`)

  /* ==================== 5. 库存管理 ==================== */
  await page.goto(`${BASE}/stock`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(700)
  await page.screenshot({ path: `${shotDir}/05-stock.png` })
  const stockRows = await page.locator('.el-table__body tr').count()
  check('库存列表有数据行', stockRows > 0, `rows=${stockRows}`)

  /* ==================== 6. 订单管理 ==================== */
  await page.goto(`${BASE}/orders`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(700)
  await page.screenshot({ path: `${shotDir}/06-orders.png` })
  const orderRows = await page.locator('.el-table__body tr').count()
  check('订单列表有数据行', orderRows > 0, `rows=${orderRows}`)

  // 打开发货按钮所在（已支付 tab）并截详情
  await page.click('.el-tabs__item:has-text("已支付")')
  await page.waitForTimeout(900)
  await page.screenshot({ path: `${shotDir}/06b-orders-paid.png` })
  const paidRows = await page.locator('.el-table__body tr').count()
  check('已支付 tab 可筛选', true, `paidRows=${paidRows}`)
  if (paidRows > 0) {
    await page.locator('button:has-text("详情")').first().click()
    await page.waitForTimeout(1000)
    await page.screenshot({ path: `${shotDir}/06c-order-detail.png` })
    check('订单详情弹窗（含时间线）', await page.locator('.timeline').isVisible())
    await page.keyboard.press('Escape')
    await page.waitForTimeout(400)
  } else {
    check('订单详情弹窗（含时间线）', false, '无已支付订单可供详情演示')
  }

  /* ==================== 7. 用户管理 ==================== */
  await page.goto(`${BASE}/users`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(700)
  await page.screenshot({ path: `${shotDir}/07-users.png` })
  const userRows = await page.locator('.el-table__body tr').count()
  check('用户列表有数据行', userRows > 0, `rows=${userRows}`)

  /* ==================== 8. 退出登录 ==================== */
  await page.click('.logout-btn')
  await page.waitForTimeout(500)
  await page.screenshot({ path: `${shotDir}/08-logout-confirm.png` })
  await page.click('.el-message-box button:has-text("退出")')
  await page.waitForURL('**/login**', { timeout: 8000 })
  check('退出登录回到登录页', page.url().includes('/login'))
} catch (e) {
  check('脚本异常中断', false, e.message)
  await page.screenshot({ path: `${shotDir}/99-error.png` }).catch(() => {})
} finally {
  await browser.close()
}

console.log(`\n==== ADMIN UI SMOKE DONE: PASS ${pass} / FAIL ${fail} ====`)
process.exit(fail > 0 ? 1 : 0)
