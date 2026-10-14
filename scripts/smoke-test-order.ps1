# =============================================================
# E-Shop order/pay smoke test - all requests go through the gateway (:9000)
# Covers: address CRUD, order create/list/detail/cancel, pay create/callback,
#         internal-path blocking, negative cases.
# Prerequisites: all 7 services running; sinon account exists (run smoke-test.ps1 first).
# Usage:  powershell -ExecutionPolicy Bypass -File scripts/smoke-test-order.ps1
# Output: %TEMP%\eshop-smoke-order\results.tsv  and one <name>.body.json per request
# NOTE: keep this file saved as UTF-8 WITH BOM (PS5.1 requires BOM for Chinese literals).
# =============================================================
$gw = 'http://127.0.0.1:9000'
$outDir = Join-Path $env:TEMP 'eshop-smoke-order'
Remove-Item $outDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Send {
    param([string]$name, [string]$method, [string]$url, [hashtable]$headers, [string]$body)
    $bodyFile = Join-Path $outDir "$name.body.json"
    $a = @('-s', '-o', $bodyFile, '-w', '%{http_code}', '-X', $method, $url)
    if ($headers) { foreach ($k in $headers.Keys) { $a += @('-H', "${k}: $($headers[$k])") } }
    if ($body) {
        # PS 5.1 向原生程序传 JSON 字符串会吞掉双引号，改为写文件 + curl @file
        $reqFile = Join-Path $outDir "$name.req.json"
        [System.IO.File]::WriteAllText($reqFile, $body, [System.Text.UTF8Encoding]::new($false))
        $a += @('-H', 'Content-Type: application/json', '--data-binary', "@$reqFile")
    }
    $code = & curl.exe @a
    Add-Content (Join-Path $outDir 'results.tsv') "$name`tHTTP $code`t$method $url"
}

function GetField([string]$file, [string]$path) {
    $json = Get-Content (Join-Path $outDir $file) -Raw -Encoding UTF8 | ConvertFrom-Json
    $node = $json
    foreach ($seg in $path.Split('.')) { $node = $node.$seg }
    return $node
}

# ---------- A. auth guard (negative) ----------
Send 'a1-address-noauth' 'GET' "$gw/api/user/addresses"
Send 'a2-order-noauth'   'POST' "$gw/api/order/create" $null '{"addressId":1,"items":[{"skuId":11,"quantity":1}]}'
Send 'a3-pay-noauth'     'POST' "$gw/api/pay/create" $null '{"orderNo":"ES0000000000000000000"}'

# ---------- B. login ----------
Send 'b1-login' 'POST' "$gw/api/user/auth/login" $null '{"username":"sinon","password":"123456"}'
$token = (Get-Content (Join-Path $outDir 'b1-login.body.json') -Raw -Encoding UTF8 | ConvertFrom-Json).data.token
Add-Content (Join-Path $outDir 'results.tsv') "b1-token`tlen=$($token.Length)"
$auth = @{ 'Authorization' = "Bearer $token" }

# ---------- C. internal paths must be blocked by gateway (404) ----------
Send 'c1-internal-product' 'POST' "$gw/api/product/internal/sku/batch" $auth '[11]'
Send 'c2-internal-stock'   'POST' "$gw/api/stock/internal/lock" $auth '{"orderNo":"ESX","items":[{"skuId":11,"quantity":1}]}'
Send 'c3-internal-order'   'GET' "$gw/api/order/internal/ESX" $auth
Send 'c4-internal-user'    'GET' "$gw/api/user/internal/addresses/1" $auth

# ---------- D. address CRUD ----------
Send 'd1-addr-list'   'GET' "$gw/api/user/addresses" $auth
Send 'd2-addr-create' 'POST' "$gw/api/user/addresses" $auth '{"receiverName":"张三","receiverPhone":"13800000001","province":"安徽省","city":"合肥市","district":"蜀山区","detailAddress":"创新大道 1 号","isDefault":false}'
$addrId = GetField 'd2-addr-create.body.json' 'data'
Add-Content (Join-Path $outDir 'results.tsv') "d2-addrId`t$addrId"
Send 'd3-addr-create2' 'POST' "$gw/api/user/addresses" $auth '{"receiverName":"李四","receiverPhone":"13900000002","province":"江苏省","city":"南京市","district":"玄武区","detailAddress":"中山路 2 号","isDefault":true}'
Send 'd4-addr-list2'  'GET' "$gw/api/user/addresses" $auth
Send 'd5-addr-update' 'PUT' "$gw/api/user/addresses/$addrId" $auth '{"receiverName":"张三","receiverPhone":"13800000001","province":"安徽省","city":"合肥市","district":"高新区","detailAddress":"望江西路 99 号","isDefault":false}'
Send 'd6-addr-404'    'PUT' "$gw/api/user/addresses/99999" $auth '{"receiverName":"王五","receiverPhone":"13700000003","detailAddress":"不存在路 1 号","isDefault":false}'

# ---------- E. order negative ----------
Send 'e1-order-badaddr' 'POST' "$gw/api/order/create" $auth '{"addressId":99999,"items":[{"skuId":11,"quantity":1}]}'
Send 'e2-order-badsku'  'POST' "$gw/api/order/create" $auth ('{"addressId":' + $addrId + ',"items":[{"skuId":99999,"quantity":1}]}')
Send 'e3-order-nostock' 'POST' "$gw/api/order/create" $auth ('{"addressId":' + $addrId + ',"items":[{"skuId":16,"quantity":99}]}')

# ---------- F. happy path: cart -> order -> pay -> callback ----------
Send 'f1-cart-add' 'POST' "$gw/api/cart/add" $auth '{"skuId":11,"quantity":2}'
Send 'f2-order-create' 'POST' "$gw/api/order/create" $auth ('{"addressId":' + $addrId + ',"remark":"冒烟测试订单","items":[{"skuId":11,"quantity":2}]}')
$orderNo = GetField 'f2-order-create.body.json' 'data.orderNo'
Add-Content (Join-Path $outDir 'results.tsv') "f2-orderNo`t$orderNo"
Send 'f3-cart-after-order' 'GET' "$gw/api/cart/list" $auth
Send 'f4-order-page' 'GET' "$gw/api/order/page?page=1&size=5" $auth
Send 'f5-order-detail' 'GET' "$gw/api/order/$orderNo" $auth
Send 'f6-stock-locked' 'GET' "$gw/api/stock/11"
Send 'f7-pay-create' 'POST' "$gw/api/pay/create" $auth ('{"orderNo":"' + $orderNo + '"}')
$paymentNo = GetField 'f7-pay-create.body.json' 'data.paymentNo'
Add-Content (Join-Path $outDir 'results.tsv') "f7-paymentNo`t$paymentNo"
Send 'f8-pay-create-again' 'POST' "$gw/api/pay/create" $auth ('{"orderNo":"' + $orderNo + '"}')
Send 'f9-callback' 'POST' "$gw/api/pay/callback" $auth ('{"paymentNo":"' + $paymentNo + '","outTradeNo":"MOCK-' + $paymentNo + '","success":true}')
Send 'f10-callback-dup' 'POST' "$gw/api/pay/callback" $auth ('{"paymentNo":"' + $paymentNo + '","outTradeNo":"MOCK-' + $paymentNo + '","success":true}')
Send 'f11-order-paid' 'GET' "$gw/api/order/$orderNo" $auth
Send 'f12-stock-after-pay' 'GET' "$gw/api/stock/11"

# ---------- G. cancel path ----------
Send 'g1-order2-create' 'POST' "$gw/api/order/create" $auth ('{"addressId":' + $addrId + ',"items":[{"skuId":13,"quantity":2}]}')
$orderNo2 = GetField 'g1-order2-create.body.json' 'data.orderNo'
Add-Content (Join-Path $outDir 'results.tsv') "g1-orderNo2`t$orderNo2"
Send 'g2-stock13-locked' 'GET' "$gw/api/stock/13"
Send 'g3-order2-cancel' 'PUT' "$gw/api/order/$orderNo2/cancel" $auth
Send 'g4-order2-detail' 'GET' "$gw/api/order/$orderNo2" $auth
Send 'g5-order2-cancel-again' 'PUT' "$gw/api/order/$orderNo2/cancel" $auth
Send 'g6-stock13-restored' 'GET' "$gw/api/stock/13"
Send 'g7-pay-cancelled' 'POST' "$gw/api/pay/create" $auth ('{"orderNo":"' + $orderNo2 + '"}')

# ---------- H. not found ----------
Send 'h1-order-404' 'GET' "$gw/api/order/ES0000000000000000000" $auth
Send 'h2-callback-404' 'POST' "$gw/api/pay/callback" $auth '{"paymentNo":"PAY0000000000000000000","outTradeNo":"MOCK-FAKE","success":true}'

# ---------- I. knife4j docs (direct service ports) ----------
Send 'i1-doc-order' 'GET' 'http://127.0.0.1:8085/doc.html'
Send 'i2-doc-pay'   'GET' 'http://127.0.0.1:8086/doc.html'

Write-Output '==== ORDER/PAY SMOKE TEST DONE ===='
Get-Content (Join-Path $outDir 'results.tsv')