/**
 * E-Shop 管理后台端到端联调（P3 阶段二）
 * 覆盖真实交互闭环：
 *   A. 商品上下架（UI 操作 + 状态回读）
 *   B. 库存调整 +5 / -5（UI 操作 + 双侧核对）
 *   C. 订单全闭环：C 端 API 下单 → 支付 → 后台 UI 发货（status=2）
 *   D. 用户禁用 / 启用（UI 操作）
 * 运行前：7 服务 + Vite(5174) 已启动。
 * 产出：scripts/shots/e2e-*.png
 */
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import fs from 'node:fs'

const require = createRequire('C:/Users/asus/.workbuddy/binaries/node/workspace/')
const { chromium } = require('playwright-core')

const GW = 'http://127.0.0.1:9000'
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

async function api(pathname, { method = 'GET', token, body } = {}) {
  const res = await fetch(GW + pathname, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  })
  return res.json()
}

/* ==================== 前置：C 端 API 造一笔已支付订单 ==================== */
const userLogin = await api('/api/user/auth/login', {
  method: 'POST',
  body: { username: 'sinon', password: '123456' }
})
const userToken = userLogin?.data?.token
check('前置：C 端用户登录', !!userToken, JSON.stringify(userLogin).slice(0, 120))

const orderRes = await api('/api/order/create', {
  method: 'POST',
  token: userToken,
  body: { addressId: 14, remark: 'admin-e2e 发货闭环', items: [{ skuId: 102, quantity: 1 }] }
})
const orderNo = orderRes?.data?.orderNo
check('前置：C 端下单成功', !!orderNo, JSON.stringify(orderRes).slice(0, 160))

const payRes = await api('/api/pay/create', { method: 'POST', token: userToken, body: { orderNo } })
const paymentNo = payRes?.data?.paymentNo
await api('/api/pay/callback', {
  method: 'POST',
  token: userToken,
  body: { paymentNo, outTradeNo: `MOCK-${paymentNo}`, success: true }
})
const paidCheck = await api(`/api/order/${orderNo}`, { token: userToken })
check('前置：C 端支付成功（status=1）', paidCheck?.data?.status === 1, `status=${paidCheck?.data?.status}`)

/* ==================== 浏览器交互 ==================== */
const browser = await chromium.launch({ executablePath: EDGE, headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

async function expectToast(text, timeout = 4000) {
  try {
    await page.locator('.el-message').filter({ hasText: text }).first().waitFor({ timeout })
    return true
  } catch {
    return false
  }
}

try {
  /* ---------- 登录 ---------- */
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入管理员账号"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', 'admin123')
  await page.click('button:has-text("登 录")')
  await page.waitForURL('**/dashboard', { timeout: 10000 })

  /* ==================== A. 商品上下架 ==================== */
  await page.goto(`${BASE}/goods`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)
  // 搜索目标商品（轻量运动鞋，id=10，当前上架中）
  await page.fill('input[placeholder="商品名称 / ID"]', '轻量运动鞋')
  await page.keyboard.press('Enter')
  await page.waitForTimeout(900)

  const targetRow = page.locator('.el-table__body tr', { hasText: '轻量运动鞋' }).first()
  await targetRow.locator('button:has-text("下架")').click()
  await page.waitForTimeout(500)
  await page.screenshot({ path: `${shotDir}/e2e-01-goods-offline-confirm.png` })
  await page.locator('.el-message-box button:has-text("下架")').click()
  const offToast = await expectToast('已下架')
  await page.waitForTimeout(600)
  const offTag = await targetRow.locator('.el-tag').textContent()
  await page.screenshot({ path: `${shotDir}/e2e-02-goods-offline-done.png` })
  check('A1 商品下架（toast + 行状态）', offToast && offTag.includes('已下架'), `toast=${offToast} tag=${offTag}`)

  // 上架恢复
  await targetRow.locator('button:has-text("上架")').click()
  await page.waitForTimeout(400)
  await page.locator('.el-message-box button:has-text("上架")').click()
  const onToast = await expectToast('已上架')
  await page.waitForTimeout(600)
  const onTag = await targetRow.locator('.el-tag').textContent()
  check('A2 商品上架恢复', onToast && onTag.includes('上架中'), `toast=${onToast} tag=${onTag}`)

  /* ==================== B. 库存调整 ==================== */
  await page.goto(`${BASE}/stock`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(900)
  const stockRow = page.locator('.el-table__body tr').first()
  const skuId = (await stockRow.locator('td').first().textContent()).trim()
  const beforeRaw = (await stockRow.locator('td').nth(2).textContent()).trim()
  const before = parseInt(beforeRaw, 10)

  await stockRow.locator('button:has-text("调整库存")').click()
  await page.waitForTimeout(400)
  await page.fill('input[placeholder="如：补货入库"]', 'admin-e2e +5')
  await page.fill('.el-dialog .el-input-number input', '+5')
  await page.screenshot({ path: `${shotDir}/e2e-03-stock-adjust.png` })
  await page.locator('.el-dialog button:has-text("确定")').click()
  const adjToast = await expectToast('库存已调整')
  await page.waitForTimeout(900)
  const mid = parseInt((await stockRow.locator('td').nth(2).textContent()).trim(), 10)

  // 还原 -5
  await stockRow.locator('button:has-text("调整库存")').click()
  await page.waitForTimeout(400)
  await page.fill('input[placeholder="如：补货入库"]', 'admin-e2e -5')
  await page.fill('.el-dialog .el-input-number input', '-5')
  await page.locator('.el-dialog button:has-text("确定")').click()
  await expectToast('库存已调整')
  await page.waitForTimeout(900)
  const after = parseInt((await stockRow.locator('td').nth(2).textContent()).trim(), 10)
  await page.screenshot({ path: `${shotDir}/e2e-04-stock-restored.png` })
  check(
    `B1 库存调整 +5/-5 生效并恢复（SKU ${skuId}）`,
    adjToast && mid === before + 5 && after === before,
    `before=${before} mid=${mid} after=${after}`
  )

  /* ==================== C. 订单发货全闭环（UI） ==================== */
  await page.goto(`${BASE}/orders`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(700)
  await page.click('.el-tabs__item:has-text("已支付")')
  await page.waitForTimeout(900)
  await page.fill('input[placeholder="订单号"]', orderNo)
  await page.keyboard.press('Enter')
  await page.waitForTimeout(900)

  const orderRow = page.locator('.el-table__body tr', { hasText: orderNo }).first()
  const rowVisible = (await orderRow.count()) > 0
  check('C1 已支付 tab 检索到新订单', rowVisible, `orderNo=${orderNo}`)
  await page.screenshot({ path: `${shotDir}/e2e-05-order-found.png` })

  await orderRow.locator('button:has-text("发货")').click()
  await page.waitForTimeout(500)
  await page.screenshot({ path: `${shotDir}/e2e-06-ship-confirm.png` })
  await page.locator('.el-message-box button:has-text("发货")').click()
  const shipToast = await expectToast('已发货')
  await page.waitForTimeout(1000)

  // 用管理端接口回读状态
  const adminLogin = await api('/api/admin/auth/login', {
    method: 'POST',
    body: { username: 'admin', password: 'admin123' }
  })
  const adminToken = adminLogin.data.token
  const orderBack = await api(`/api/admin/order/${orderNo}`, { token: adminToken })
  await page.screenshot({ path: `${shotDir}/e2e-07-shipped.png` })
  check(
    'C2 后台发货成功（status=2）',
    shipToast && orderBack?.data?.status === 2,
    `toast=${shipToast} status=${orderBack?.data?.status}`
  )

  /* ==================== D. 用户禁用 / 启用 ==================== */
  await page.goto(`${BASE}/users`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)
  const userRow = page.locator('.el-table__body tr', { hasText: 'sinon' }).first()
  await userRow.locator('button:has-text("禁用")').click()
  await page.waitForTimeout(400)
  await page.locator('.el-message-box button:has-text("禁用")').click()
  const disableToast = await expectToast('已禁用')
  await page.waitForTimeout(800)
  const disabledTag = await userRow.locator('.el-tag').textContent()
  await page.screenshot({ path: `${shotDir}/e2e-08-user-disabled.png` })

  await userRow.locator('button:has-text("启用")').click()
  await page.waitForTimeout(400)
  await page.locator('.el-message-box button:has-text("启用")').click()
  const enableToast = await expectToast('已启用')
  await page.waitForTimeout(800)
  const enabledTag = await userRow.locator('.el-tag').textContent()
  check(
    'D1 用户禁用 / 启用（状态回读）',
    disableToast && disabledTag.includes('已禁用') && enableToast && enabledTag.includes('正常'),
    `dis=${disabledTag} en=${enabledTag}`
  )
} catch (e) {
  check('脚本异常中断', false, e.message)
  await page.screenshot({ path: `${shotDir}/e2e-99-error.png` }).catch(() => {})
} finally {
  await browser.close()
}

console.log(`\n==== ADMIN E2E DONE: PASS ${pass} / FAIL ${fail} ====`)
process.exit(fail > 0 ? 1 : 0)
