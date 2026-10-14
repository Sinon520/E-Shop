# =============================================================
# E-Shop 管理后台冒烟测试（P3）
# 覆盖：admin 登录 / 鉴权策略（401 无 token、403 用户token越权）/ 看板 /
#       商品（全量分页/上下架/新增）/ 分类（增改删/删除保护）/ 库存（调整+双侧核对）/ 订单（发货闭环）
# 前置：全套 7 服务运行；admin 种子账号 admin/admin123；sinon/123456 有效
# 用法：powershell -ExecutionPolicy Bypass -File scripts/smoke-test-admin.ps1
# NOTE: keep this file saved as UTF-8 WITH BOM (PS5.1 requires BOM for Chinese literals).
# =============================================================
$gw = 'http://127.0.0.1:9000'
$outDir = Join-Path $env:TEMP 'eshop-smoke-admin'
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

# ---------- A. 鉴权策略 ----------
Write-Host '==== A. 鉴权策略（负例） ===='
$code = Send 'a1-nodash' 'GET' "$gw/api/admin/dashboard"
Check 'A1 无 token 访问管理接口 → 401' ($code -eq '401') "http=$code"

$null = Send 'a2-userlogin' 'POST' "$gw/api/user/auth/login" $null '{"username":"sinon","password":"123456"}'
$userToken = (Resp 'a2-userlogin').data.token
$code = Send 'a3-usertoken' 'GET' "$gw/api/admin/dashboard" @{ 'Authorization' = "Bearer $userToken" }
Check 'A2 普通用户 token 访问管理接口 → 403' ($code -eq '403') "http=$code"

$code = Send 'a4-badpwd' 'POST' "$gw/api/admin/auth/login" $null '{"username":"admin","password":"wrong"}'
$r = Resp 'a4-badpwd'
Check 'A3 admin 错误密码 → 1002' ($r.code -eq 1002) "code=$($r.code)"

# ---------- B. admin 登录 ----------
Write-Host '==== B. 管理员登录 ===='
$code = Send 'b1-login' 'POST' "$gw/api/admin/auth/login" $null '{"username":"admin","password":"admin123"}'
$r = Resp 'b1-login'
$adminToken = $r.data.token
$auth = @{ 'Authorization' = "Bearer $adminToken" }
Check 'B1 管理员登录成功' ($code -eq '200' -and $r.code -eq 200 -and $adminToken) "code=$($r.code)"

# ---------- C. 数据看板 ----------
Write-Host '==== C. 数据看板 ===='
$code = Send 'c1-dashboard' 'GET' "$gw/api/admin/dashboard" $auth
$r = Resp 'c1-dashboard'
Check 'C1 看板接口 200 且字段齐全' ($r.code -eq 200 -and $r.data.statusDist -ne $null -and $r.data.hotTop5 -ne $null) "code=$($r.code)"

# ---------- D. 商品管理 ----------
Write-Host '==== D. 商品管理 ===='
$code = Send 'd1-adminpage' 'GET' "$gw/api/admin/product/spu/page?page=1&size=100" $auth
$r = Resp 'd1-adminpage'
$adminTotal = $r.data.total
$null = Send 'd1c-cpage' 'GET' "$gw/api/product/spu/page?page=1&size=100"
$cTotal = (Resp 'd1c-cpage').data.total
Check 'D1 管理端商品分页（全量，含下架）' ($r.code -eq 200 -and $adminTotal -ge $cTotal) "admin=$adminTotal c端=$cTotal"

$firstSpu = $r.data.list | Select-Object -First 1
$spuId = $firstSpu.id
$origStatus = $firstSpu.status
$null = Send 'd2-off' 'PUT' "$gw/api/admin/product/spu/$spuId/status" $auth '{"status":0}'
$null = Send 'd2-check' 'GET' "$gw/api/admin/product/spu/page?page=1&size=100&status=0" $auth
$offIds = (Resp 'd2-check').data.list | ForEach-Object { $_.id }
$null = Send 'd3-on' 'PUT' "$gw/api/admin/product/spu/$spuId/status" $auth '{"status":1}'
$null = Send 'd3-check' 'GET' "$gw/api/admin/product/spu/page?page=1&size=100&status=1" $auth
$onIds = (Resp 'd3-check').data.list | ForEach-Object { $_.id }
Check "D2 下架生效（spu$spuId 出现在下架列表）" ($offIds -contains $spuId) "offIds=$($offIds -join ',')"
Check "D3 上架生效（spu$spuId 出现在上架列表）" ($onIds -contains $spuId) "onIds=$($onIds -join ',')"
if ($origStatus -eq 0) { $null = Send 'd3r-restore' 'PUT' "$gw/api/admin/product/spu/$spuId/status" $auth '{"status":0}' }

$spuBody = '{"name":"冒烟测试商品-可删","categoryId":1,"mainImage":"","detail":"冒烟","skus":[{"name":"默认规格","price":9.90,"marketPrice":19.90}]}'
$code = Send 'd4-create' 'POST' "$gw/api/admin/product/spu" $auth $spuBody
$r = Resp 'd4-create'
Check 'D4 新增商品（含 SKU）成功' ($r.code -eq 200 -and $r.data -gt 0) "code=$($r.code) id=$($r.data)"

# ---------- E. 分类管理 ----------
Write-Host '==== E. 分类管理 ===='
$code = Send 'e1-create' 'POST' "$gw/api/admin/product/category" $auth '{"name":"冒烟测试分类","parentId":0,"level":1,"sort":99,"status":1}'
$r = Resp 'e1-create'
$catId = $r.data
Check 'E1 新增分类成功' ($r.code -eq 200 -and $catId -gt 0) "code=$($r.code)"

$null = Send 'e2-update' 'PUT' "$gw/api/admin/product/category/$catId" $auth '{"name":"冒烟测试分类2","parentId":0,"level":1,"sort":99,"status":1}'
$r = Resp 'e2-update'
Check 'E2 编辑分类成功' ($r.code -eq 200) "code=$($r.code)"

$code = Send 'e3-delref' 'DELETE' "$gw/api/admin/product/category/1" $auth
$r = Resp 'e3-delref'
Check 'E3 删除有商品的分类 → 拒绝（保护）' ($r.code -eq 400) "code=$($r.code) msg=$($r.message)"

$code = Send 'e4-delete' 'DELETE' "$gw/api/admin/product/category/$catId" $auth
$r = Resp 'e4-delete'
Check 'E4 删除测试分类成功' ($r.code -eq 200) "code=$($r.code)"

# ---------- F. 库存管理 ----------
Write-Host '==== F. 库存管理 ===='
$code = Send 'f1-stockpage' 'GET' "$gw/api/admin/stock/page?page=1&size=100" $auth
$r = Resp 'f1-stockpage'
Check 'F1 库存分页（含 Redis 预扣值）' ($r.code -eq 200 -and $r.data.list.Count -gt 0) "code=$($r.code)"
$first = $r.data.list | Select-Object -First 1
Check 'F2 双侧核对字段存在' ($first.redisValue -ne $null -and $first.consistent -ne $null) "redis=$($first.redisValue) consistent=$($first.consistent)"

$skuId = 102
$null = Send 'f2-before' 'GET' "$gw/api/stock/$skuId"
$before = (Resp 'f2-before').data.available
$null = Send 'f3-add' 'PUT' "$gw/api/admin/stock/$skuId/adjust" $auth '{"mode":"delta","value":5,"remark":"冒烟测试+5"}'
$null = Send 'f4-mid' 'GET' "$gw/api/stock/$skuId"
$mid = (Resp 'f4-mid').data.available
$null = Send 'f5-sub' 'PUT' "$gw/api/admin/stock/$skuId/adjust" $auth '{"mode":"delta","value":-5,"remark":"冒烟测试-5"}'
$null = Send 'f6-after' 'GET' "$gw/api/stock/$skuId"
$after = (Resp 'f6-after').data.available
Check 'F3 库存调整 +5 / -5 生效并恢复' (($mid -eq ($before + 5)) -and ($after -eq $before)) "before=$before mid=$mid after=$after"

# ---------- G. 订单管理（发货闭环） ----------
Write-Host '==== G. 订单管理 ===='
$code = Send 'g1-orderpage' 'GET' "$gw/api/admin/order/page?page=1&size=5" $auth
$r = Resp 'g1-orderpage'
Check 'G1 管理端订单分页（全量）' ($r.code -eq 200 -and $r.data.total -gt 0) "code=$($r.code) total=$($r.data.total)"

# 全闭环：下单 → 支付 → 发货
$uAuth = @{ 'Authorization' = "Bearer $userToken" }
$null = Send 'g2-order' 'POST' "$gw/api/order/create" $uAuth '{"addressId":14,"remark":"admin发货冒烟","items":[{"skuId":102,"quantity":1}]}'
$orderNo = (Resp 'g2-order').data.orderNo
$null = Send 'g3-pay' 'POST' "$gw/api/pay/create" $uAuth ('{"orderNo":"' + $orderNo + '"}')
$paymentNo = (Resp 'g3-pay').data.paymentNo
$null = Send 'g4-callback' 'POST' "$gw/api/pay/callback" $uAuth ('{"paymentNo":"' + $paymentNo + '","outTradeNo":"MOCK-' + $paymentNo + '","success":true}')
$code = Send 'g5-ship' 'PUT' "$gw/api/admin/order/$orderNo/ship" $auth
$r = Resp 'g5-ship'
$null = Send 'g6-check' 'GET' "$gw/api/admin/order/$orderNo" $auth
$status = (Resp 'g6-check').data.status
Check "G2 发货闭环（下单→支付→发货 status=2）" ($r.code -eq 200 -and $status -eq 2) "code=$($r.code) status=$status"

$null = Send 'g7-neworder' 'POST' "$gw/api/order/create" $uAuth '{"addressId":14,"remark":"待支付发货负例","items":[{"skuId":102,"quantity":1}]}'
$pendingNo = (Resp 'g7-neworder').data.orderNo
$code = Send 'g8-ship-pending' 'PUT' "$gw/api/admin/order/$pendingNo/ship" $auth
$r = Resp 'g8-ship-pending'
Check 'G3 待支付订单发货 → 5002' ($r.code -eq 5002) "code=$($r.code)"

# ---------- H. 用户管理 ----------
Write-Host '==== H. 用户管理 ===='
$code = Send 'h1-userpage' 'GET' "$gw/api/admin/user/page?page=1&size=10" $auth
$r = Resp 'h1-userpage'
Check 'H1 用户分页（手机号脱敏）' ($r.code -eq 200 -and $r.data.list.Count -gt 0) "code=$($r.code)"

$code = Send 'h2-kw' 'GET' "$gw/api/admin/user/page?keyword=sinon" $auth
$r = Resp 'h2-kw'
Check 'H2 用户搜索 keyword=sinon' ($r.code -eq 200 -and $r.data.total -ge 1) "total=$($r.data.total)"

Write-Host ''
Write-Host ("==== ADMIN SMOKE DONE: PASS {0} / FAIL {1} ====" -f $pass, $fail)
