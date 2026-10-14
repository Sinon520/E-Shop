# =============================================================
# E-Shop API smoke test - all requests go through the gateway (:9000)
# Prerequisites: nacos + mysql + redis up; gateway/user/product/stock/cart running.
# Usage:  powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1
# Output: %TEMP%\eshop-smoke\results.tsv  and one <name>.body.json per request
# =============================================================
$gw = 'http://127.0.0.1:9000'
$outDir = Join-Path $env:TEMP 'eshop-smoke'
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

# ---------- A. public endpoints (no auth) ----------
Send 'a1-health'         'GET' "$gw/api/user/health"
Send 'a2-categories'     'GET' "$gw/api/product/categories"
Send 'a3-page-default'   'GET' "$gw/api/product/spu/page?size=4"
Send 'a4-page-sales'     'GET' "$gw/api/product/spu/page?sort=sales&size=3"
Send 'a5-page-priceasc'  'GET' "$gw/api/product/spu/page?sort=priceAsc&size=100"
Send 'a6-page-pricedesc' 'GET' "$gw/api/product/spu/page?sort=priceDesc&size=3"
Send 'a7-page-cat1'      'GET' "$gw/api/product/spu/page?categoryId=1"
Send 'a8-page-search'    'GET' "$gw/api/product/spu/page?keyword=%E8%80%B3%E6%9C%BA"
Send 'a9-page-p2'        'GET' "$gw/api/product/spu/page?page=2&size=3"
Send 'a10-spu-detail'    'GET' "$gw/api/product/spu/1"
Send 'a11-stock-11'      'GET' "$gw/api/stock/11"
Send 'a12-spu-999'       'GET' "$gw/api/product/spu/999"
Send 'a13-stock-99999'   'GET' "$gw/api/stock/99999"

# ---------- B. auth guard (negative) ----------
Send 'b1-cart-noauth'    'GET' "$gw/api/cart/list"
Send 'b2-cart-spoof'     'GET' "$gw/api/cart/list" @{ 'X-User-Id' = '12345' }
Send 'b3-profile-bad'    'GET' "$gw/api/user/profile" @{ 'Authorization' = 'Bearer bad.token.value' }

# ---------- C. register / login ----------
Send 'c1-register'       'POST' "$gw/api/user/auth/register" $null '{"username":"sinon","password":"123456","phone":"13800000000"}'
Send 'c2-register-dup'   'POST' "$gw/api/user/auth/register" $null '{"username":"sinon","password":"123456"}'
Send 'c3-login-badpwd'   'POST' "$gw/api/user/auth/login" $null '{"username":"sinon","password":"wrong"}'
Send 'c4-login'          'POST' "$gw/api/user/auth/login" $null '{"username":"sinon","password":"123456"}'
$token = (Get-Content (Join-Path $outDir 'c4-login.body.json') -Raw -Encoding UTF8 | ConvertFrom-Json).data.token
Add-Content (Join-Path $outDir 'results.tsv') "c4-token`tlen=$($token.Length)"
$auth = @{ 'Authorization' = "Bearer $token" }
Send 'c5-profile'        'GET' "$gw/api/user/profile" $auth

# ---------- D. cart (needs auth) ----------
Send 'd1-list-empty'     'GET' "$gw/api/cart/list" $auth
Send 'd2-add-11'         'POST' "$gw/api/cart/add" $auth '{"skuId":11,"quantity":2}'
Send 'd3-add-12'         'POST' "$gw/api/cart/add" $auth '{"skuId":12,"quantity":1}'
Send 'd4-list-2items'    'GET' "$gw/api/cart/list" $auth
Send 'd5-qty-11-5'       'PUT' "$gw/api/cart/quantity" $auth '{"skuId":11,"quantity":5}'
Send 'd6-check-11-off'   'PUT' "$gw/api/cart/checked" $auth '{"skuId":11,"checked":false}'
Send 'd7-checkall-on'    'PUT' "$gw/api/cart/checked-all" $auth '{"checked":true}'
Send 'd8-list-checked'   'GET' "$gw/api/cart/list" $auth
Send 'd9-del-12'         'DELETE' "$gw/api/cart/12" $auth
Send 'd10-batchremove-11' 'POST' "$gw/api/cart/batch-remove" $auth '{"skuIds":[11]}'
Send 'd11-list-final'    'GET' "$gw/api/cart/list" $auth
Send 'd12-add-badsku'    'POST' "$gw/api/cart/add" $auth '{"skuId":99999,"quantity":1}'
Send 'd13-qty-missing'   'PUT' "$gw/api/cart/quantity" $auth '{"skuId":11,"quantity":3}'

# ---------- E. knife4j docs (direct service ports) ----------
Send 'e1-doc-user'       'GET' 'http://127.0.0.1:8081/doc.html'
Send 'e2-doc-product'    'GET' 'http://127.0.0.1:8082/doc.html'
Send 'e3-doc-cart'       'GET' 'http://127.0.0.1:8084/doc.html'
Send 'e4-doc-stock'      'GET' 'http://127.0.0.1:8083/doc.html'

Write-Output '==== SMOKE TEST DONE ===='
Get-Content (Join-Path $outDir 'results.tsv')
