// =====================================================================
// E-Shop 并发压测脚本（Node 原生 fetch，无第三方依赖）
// 用法: node pressure-test.mjs [并发数] [时长秒] [skuId]
// 默认: 30 并发 × 20 秒，skuId=103（压测基线 SKU）
// 输出: 控制台统计 + scripts/pressure-result.json（结构化结果）
// =====================================================================
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const BASE = process.env.ESHOP_BASE ?? 'http://127.0.0.1:9000';
const CONCURRENCY = Number(process.argv[2] ?? 30);
const DURATION_S = Number(process.argv[3] ?? 20);
const SKU_ID = Number(process.argv[4] ?? 103);
const REMARK = '压测-node';
const ADDRESS_ID = Number(process.env.ESHOP_ADDRESS_ID ?? 14);
const USER = { username: 'sinon', password: '123456' };

async function login() {
  const res = await fetch(`${BASE}/api/user/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(USER),
  });
  const j = await res.json();
  if (j.code !== 200 || !j.data?.token) throw new Error('登录失败: ' + JSON.stringify(j));
  return j.data.token;
}

const stats = {
  total: 0,
  ok: 0,
  soldOut: 0,
  blocked429: 0,
  otherBiz: new Map(),
  httpFail: 0,
  error: 0,
  latencies: [],
  okLatencies: [],
  soldOutLatencies: [],
  blockedLatencies: [],
};

function record(code, ms, httpStatus) {
  stats.total++;
  stats.latencies.push(ms);
  if (httpStatus === 429) {
    stats.blocked429++;
    stats.blockedLatencies.push(ms);
    return;
  }
  if (httpStatus !== 200) {
    stats.httpFail++;
    return;
  }
  if (code === 200) {
    stats.ok++;
    stats.okLatencies.push(ms);
  } else if (code === 3002) {
    stats.soldOut++;
    stats.soldOutLatencies.push(ms);
  } else {
    stats.otherBiz.set(code, (stats.otherBiz.get(code) ?? 0) + 1);
  }
}

async function worker(token, deadline) {
  const body = JSON.stringify({
    addressId: ADDRESS_ID,
    remark: REMARK,
    items: [{ skuId: SKU_ID, quantity: 1 }],
  });
  while (Date.now() < deadline) {
    const start = performance.now();
    try {
      const res = await fetch(`${BASE}/api/order/create`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body,
      });
      const ms = performance.now() - start;
      const j = await res.json().catch(() => ({}));
      record(j.code ?? -1, ms, res.status);
    } catch {
      stats.error++;
      stats.total++;
    }
  }
}

function percentile(sorted, p) {
  if (!sorted.length) return 0;
  const idx = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[Math.max(0, idx)];
}

const avg = (arr) => (arr.length ? arr.reduce((s, v) => s + v, 0) / arr.length : 0);

(async () => {
  console.log('==== E-Shop 压测 ====');
  console.log(`目标: POST ${BASE}/api/order/create  (skuId=${SKU_ID})`);
  console.log(`并发: ${CONCURRENCY}  时长: ${DURATION_S}s\n`);

  const token = await login();
  console.log('登录成功，开始压测...\n');

  const startAt = Date.now();
  const deadline = startAt + DURATION_S * 1000;
  await Promise.all(Array.from({ length: CONCURRENCY }, () => worker(token, deadline)));
  const elapsed = (Date.now() - startAt) / 1000;

  const sorted = [...stats.latencies].sort((a, b) => a - b);
  const okSorted = [...stats.okLatencies].sort((a, b) => a - b);
  const soSorted = [...stats.soldOutLatencies].sort((a, b) => a - b);
  const blockedSorted = [...stats.blockedLatencies].sort((a, b) => a - b);

  const result = {
    target: `${BASE}/api/order/create`,
    skuId: SKU_ID,
    concurrency: CONCURRENCY,
    durationSec: Number(elapsed.toFixed(2)),
    total: stats.total,
    ok: stats.ok,
    soldOut3002: stats.soldOut,
    blocked429: stats.blocked429,
    otherBiz: Object.fromEntries(stats.otherBiz),
    httpFail: stats.httpFail,
    networkError: stats.error,
    rps: Number((stats.total / elapsed).toFixed(2)),
    latencyMs: {
      all: {
        avg: Number(avg(sorted).toFixed(2)),
        p50: Number(percentile(sorted, 50).toFixed(2)),
        p95: Number(percentile(sorted, 95).toFixed(2)),
        p99: Number(percentile(sorted, 99).toFixed(2)),
        max: Number((sorted.at(-1) ?? 0).toFixed(2)),
      },
      ok: {
        avg: Number(avg(okSorted).toFixed(2)),
        p95: Number(percentile(okSorted, 95).toFixed(2)),
      },
      soldOut: {
        avg: Number(avg(soSorted).toFixed(2)),
        p95: Number(percentile(soSorted, 95).toFixed(2)),
      },
      blocked429: {
        avg: Number(avg(blockedSorted).toFixed(2)),
        p95: Number(percentile(blockedSorted, 95).toFixed(2)),
      },
    },
  };

  console.log('---------- 压测结果 ----------');
  console.log(`实际耗时:        ${result.durationSec} s`);
  console.log(`总请求数:        ${result.total}`);
  console.log(`成功下单(200):   ${result.ok}`);
  console.log(`库存不足(3002):  ${result.soldOut3002}`);
  console.log(`限流拒绝(429):   ${result.blocked429}`);
  for (const [c, n] of stats.otherBiz) console.log(`其他业务码 ${c}:   ${n}`);
  console.log(`HTTP 失败:       ${result.httpFail}`);
  console.log(`网络异常:        ${result.networkError}`);
  console.log(`平均 RPS:        ${result.rps}`);
  console.log('---------- 响应时间 (ms) ----------');
  console.log(`全部:   平均 ${result.latencyMs.all.avg} / P50 ${result.latencyMs.all.p50} / P95 ${result.latencyMs.all.p95} / P99 ${result.latencyMs.all.p99} / Max ${result.latencyMs.all.max}`);
  console.log(`成功单: 平均 ${result.latencyMs.ok.avg} / P95 ${result.latencyMs.ok.p95}`);
  console.log(`拒绝单: 平均 ${result.latencyMs.soldOut.avg} / P95 ${result.latencyMs.soldOut.p95}`);
  console.log(`限流429: 平均 ${result.latencyMs.blocked429.avg} / P95 ${result.latencyMs.blocked429.p95}`);
  console.log('------------------------------');

  const outPath = join(dirname(fileURLToPath(import.meta.url)), 'pressure-result.json');
  writeFileSync(outPath, JSON.stringify(result, null, 2), 'utf-8');
  console.log(`结果已写入: ${outPath}`);
})();
