# =============================================================
# E-Shop 库存 Redis Lua 预扣冒烟测试（P2-4 防超卖第一层）
# 覆盖：预热 → 下单双侧扣减一致 → 余量不足 Lua 层拦截 → 取消双侧回补一致
# 前置：全套 7 服务 + Redis 运行
# 用法：powershell -ExecutionPolicy Bypass -File scripts/smoke-test-stock-lua.ps1
# NOTE: keep this file saved as UTF-8 WITH BOM (PS5.1 requires BOM for Chinese literals).
# =============================================================
$gw = 'http://127.0.0.1:9000'
$svc = 'http://127.0.0.1:8083'
$outDir = Join-Path $env:TEMP 'eshop-stock-lua'
Remove-Item $outDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

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

function Resp { param([string]$name)
    return (Get-Content (Join-Path $outDir "$name.body.json") -Raw -Encoding UTF8 | ConvertFrom-Json)
}

function Inspect { param([string]$name, [long]$skuId)
    $null = Send $name 'GET' "$svc/internal/stock/redis/$skuId"
    return (Resp $name).data
}

# ---------- 1. 准备 ----------
Write-Host '==== 1. 准备（登录/地址/预热） ===='
$null = Send 'l1-login' 'POST' "$gw/api/user/auth/login" $null '{"username":"sinon","password":"123456"}'
$r = Resp 'l1-login'
$token = $r.data.token
$auth = @{ 'Authorization' = "Bearer $token" }
Check '1.1 登录成功' ($r.code -eq 200 -and $token) "code=$($r.code)"

$null = Send 'l2-addr' 'GET' "$gw/api/user/addresses" $auth
$addrId = (Resp 'l2-addr').data[0].id
Check '1.2 地址就绪' ($addrId -ne $null) "addrId=$addrId"

$null = Send 'l3-preheat' 'POST' "$svc/internal/stock/preheat"
$r = Resp 'l3-preheat'
Check '1.3 Redis 预热接口' ($r.code -eq 200 -and $r.data -like '*预热完成*') "data=$($r.data)"

# ---------- 2. 正常下单：双侧扣减一致 ----------
Write-Host '==== 2. 下单 = Redis 预扣 + DB 双扣一致 ===='
$before = Inspect 's1-before' 11
Check '2.1 基线两侧一致' (($before.redisValue -ne $null) -and ($before.consistent -eq $true)) "db=$($before.dbAvailable) redis=$($before.redisValue)"

$null = Send 's2-order' 'POST' "$gw/api/order/create" $auth ('{"addressId":' + $addrId + ',"remark":"Lua预扣冒烟","items":[{"skuId":11,"quantity":1}]}')
$r = Resp 's2-order'
$orderNo = $r.data.orderNo
Check '2.2 下单成功' ($r.code -eq 200 -and $orderNo) "code=$($r.code)"
Write-Host "      orderNo = $orderNo" -ForegroundColor Gray

$after = Inspect 's3-after' 11
Check '2.3 下单后两侧同步 -1' (($after.dbAvailable -eq ($before.dbAvailable - 1)) -and ($after.redisValue -eq ($before.redisValue - 1)) -and ($after.consistent -eq $true)) "db=$($after.dbAvailable) redis=$($after.redisValue)"

# ---------- 3. 余量不足：Lua 层拦截，两侧不动 ----------
Write-Host '==== 3. 余量不足 = Lua 层拦截（不打 DB） ===='
$b16 = Inspect 's4-sku16-before' 16
$null = Send 's5-order-fail' 'POST' "$gw/api/order/create" $auth ('{"addressId":' + $addrId + ',"items":[{"skuId":16,"quantity":99}]}')
$r = Resp 's5-order-fail'
Check '3.1 余量不足返回 3002' ($r.code -eq 3002) "code=$($r.code)"

$a16 = Inspect 's6-sku16-after' 16
Check '3.2 拦截后两侧均未扣减' (($a16.dbAvailable -eq $b16.dbAvailable) -and ($a16.redisValue -eq $b16.redisValue)) "db=$($a16.dbAvailable) redis=$($a16.redisValue)"

# ---------- 4. 取消订单：双侧回补一致 ----------
Write-Host '==== 4. 取消回补 = Redis + DB 双回 ===='
$null = Send 's7-cancel' 'PUT' "$gw/api/order/$orderNo/cancel" $auth
$r = Resp 's7-cancel'
Check '4.1 取消成功' ($r.code -eq 200) "code=$($r.code)"

$final = Inspect 's8-final' 11
Check '4.2 取消后两侧回到基线' (($final.dbAvailable -eq $before.dbAvailable) -and ($final.redisValue -eq $before.redisValue) -and ($final.consistent -eq $true)) "db=$($final.dbAvailable) redis=$($final.redisValue)"

Write-Host ''
Write-Host ("==== STOCK LUA SMOKE DONE: PASS {0} / FAIL {1} ====" -f $pass, $fail)
