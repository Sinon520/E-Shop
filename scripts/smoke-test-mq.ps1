# =============================================================
# E-Shop RocketMQ 超时关单冒烟测试（P2-2）
# 场景：下单（发延迟消息）→ 到期未支付 → 自动关闭(5) + 回补库存；
#       已支付(1) / 已取消(4) 订单收到超时消息不误处理（幂等）。
# 前置：全套 7 服务 + RocketMQ 运行；order 以 --eshop.mq.order-timeout-delay-level=3（10s）启动
# 用法：powershell -ExecutionPolicy Bypass -File scripts/smoke-test-mq.ps1
# NOTE: keep this file saved as UTF-8 WITH BOM (PS5.1 requires BOM for Chinese literals).
# =============================================================
$gw = 'http://127.0.0.1:9000'
$outDir = Join-Path $env:TEMP 'eshop-smoke-mq'
Remove-Item $outDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$waitSeconds = 14

$pass = 0; $fail = 0
function Check {
    param([string]$name, [bool]$ok, [string]$detail)
    if ($ok) { $script:pass++; Write-Host ("[PASS] " + $name) -ForegroundColor Green }
    else { $script:fail++; Write-Host ("[FAIL] " + $name + "  => " + $detail) -ForegroundColor Red }
}

function Send {
    param([string]$name, [string]$method, [string]$url, [hashtable]$headers, [string]$body)
    $bodyFile = Join-Path $outDir "$name.body.json"
    $a = @('-s', '-o', $bodyFile, '-w', '%{http_code}', '-X', $method, $url)
    if ($headers) { foreach ($k in $headers.Keys) { $a += @('-H', "${k}: $($headers[$k])") } }
    if ($body) {
        $reqFile = Join-Path $outDir "$name.req.json"
        [System.IO.File]::WriteAllText($reqFile, $body, [System.Text.UTF8Encoding]::new($false))
        $a += @('-H', 'Content-Type: application/json', '--data-binary', "@$reqFile")
    }
    $code = & curl.exe @a
    return $code
}

function Resp {
    param([string]$name)
    return (Get-Content (Join-Path $outDir "$name.body.json") -Raw -Encoding UTF8 | ConvertFrom-Json)
}

# ---------- 1. 登录 + 地址 ----------
Write-Host '==== 1. 准备（登录/地址） ===='
$null = Send 'l1-login' 'POST' "$gw/api/user/auth/login" $null '{"username":"sinon","password":"123456"}'
$r = Resp 'l1-login'
$token = $r.data.token
$auth = @{ 'Authorization' = "Bearer $token" }
Check '1.1 登录成功' ($r.code -eq 200 -and $token) "code=$($r.code)"

$null = Send 'l2-addr' 'GET' "$gw/api/user/addresses" $auth
$r = Resp 'l2-addr'
if ($r.data.Count -gt 0) {
    $addrId = $r.data[0].id
} else {
    $null = Send 'l2b-addr-create' 'POST' "$gw/api/user/addresses" $auth '{"receiverName":"张三","receiverPhone":"13800000001","province":"安徽省","city":"合肥市","district":"蜀山区","detailAddress":"创新大道 1 号","isDefault":true}'
    $addrId = (Resp 'l2b-addr-create').data
}
Check '1.2 地址就绪' ($addrId -ne $null) "addrId=$addrId"

# ---------- 2. 场景 A：超时未支付 → 自动关闭 + 回补库存 ----------
Write-Host '==== 2. 场景A：超时关单主链路 ===='
$null = Send 'a1-stock-before' 'GET' "$gw/api/stock/11"
$stockBefore = (Resp 'a1-stock-before').data.available

$null = Send 'a2-create' 'POST' "$gw/api/order/create" $auth ('{"addressId":' + $addrId + ',"remark":"超时关单测试A","items":[{"skuId":11,"quantity":1}]}')
$r = Resp 'a2-create'
$orderNoA = $r.data.orderNo
Check '2.1 下单成功' ($r.code -eq 200 -and $orderNoA) "code=$($r.code)"
Write-Host "      orderNo(A) = $orderNoA" -ForegroundColor Gray

$null = Send 'a3-detail-pending' 'GET' "$gw/api/order/$orderNoA" $auth
$r = Resp 'a3-detail-pending'
Check '2.2 下单后为待支付(0)' ($r.data.status -eq 0) "status=$($r.data.status)"

$null = Send 'a4-stock-locked' 'GET' "$gw/api/stock/11"
$r = Resp 'a4-stock-locked'
Check '2.3 下单后库存已锁定' ($r.data.available -eq ($stockBefore - 1)) "before=$stockBefore now=$($r.data.available)"

Write-Host ("      ... 等待 {0}s（延迟 10s + 消费余量）..." -f $waitSeconds) -ForegroundColor Gray
Start-Sleep -Seconds $waitSeconds

$null = Send 'a5-detail-closed' 'GET' "$gw/api/order/$orderNoA" $auth
$r = Resp 'a5-detail-closed'
Check '2.4 到期后自动关闭(5)' ($r.data.status -eq 5) "status=$($r.data.status)"

$null = Send 'a6-stock-restored' 'GET' "$gw/api/stock/11"
$r = Resp 'a6-stock-restored'
Check '2.5 库存已回补' ($r.data.available -eq $stockBefore) "before=$stockBefore now=$($r.data.available)"

# ---------- 3. 场景 B：已支付订单不被超时误关 ----------
Write-Host '==== 3. 场景B：已支付订单不被误关 ===='
$null = Send 'b1-create' 'POST' "$gw/api/order/create" $auth ('{"addressId":' + $addrId + ',"remark":"超时关单测试B","items":[{"skuId":11,"quantity":1}]}')
$orderNoB = (Resp 'b1-create').data.orderNo
Write-Host "      orderNo(B) = $orderNoB" -ForegroundColor Gray
$null = Send 'b2-pay-create' 'POST' "$gw/api/pay/create" $auth ('{"orderNo":"' + $orderNoB + '"}')
$paymentNo = (Resp 'b2-pay-create').data.paymentNo
$null = Send 'b3-callback' 'POST' "$gw/api/pay/callback" $auth ('{"paymentNo":"' + $paymentNo + '","outTradeNo":"MOCK-' + $paymentNo + '","success":true}')
$null = Send 'b4-detail-paid' 'GET' "$gw/api/order/$orderNoB" $auth
$r = Resp 'b4-detail-paid'
Check '3.1 支付后为已支付(1)' ($r.data.status -eq 1) "status=$($r.data.status)"

Write-Host ("      ... 等待 {0}s，让超时消息到达 ..." -f $waitSeconds) -ForegroundColor Gray
Start-Sleep -Seconds $waitSeconds

$null = Send 'b5-detail-after' 'GET' "$gw/api/order/$orderNoB" $auth
$r = Resp 'b5-detail-after'
Check '3.2 超时消息到达后仍为已支付(1)（未误关）' ($r.data.status -eq 1) "status=$($r.data.status)"

# ---------- 4. 场景 C：已取消订单不被超时消息重复处理 ----------
Write-Host '==== 4. 场景C：已取消订单不重复处理 ===='
$null = Send 'c1-create' 'POST' "$gw/api/order/create" $auth ('{"addressId":' + $addrId + ',"remark":"超时关单测试C","items":[{"skuId":13,"quantity":1}]}')
$orderNoC = (Resp 'c1-create').data.orderNo
Write-Host "      orderNo(C) = $orderNoC" -ForegroundColor Gray
$null = Send 'c2-cancel' 'PUT' "$gw/api/order/$orderNoC/cancel" $auth
$null = Send 'c3-detail-cancelled' 'GET' "$gw/api/order/$orderNoC" $auth
$r = Resp 'c3-detail-cancelled'
Check '4.1 用户取消后为已取消(4)' ($r.data.status -eq 4) "status=$($r.data.status)"

Write-Host ("      ... 等待 {0}s，让超时消息到达 ..." -f $waitSeconds) -ForegroundColor Gray
Start-Sleep -Seconds $waitSeconds

$null = Send 'c4-detail-after' 'GET' "$gw/api/order/$orderNoC" $auth
$r = Resp 'c4-detail-after'
Check '4.2 超时消息到达后仍为已取消(4)（幂等跳过）' ($r.data.status -eq 4) "status=$($r.data.status)"

Write-Host ''
Write-Host ("==== MQ SMOKE DONE: PASS {0} / FAIL {1} ====" -f $pass, $fail)
