# =============================================================
# E-Shop 商品缓存冒烟测试（P2-1 缓存三件套 + 延迟双删）
# 前置：nacos + mysql + redis up；eshop-product running
# 说明：直连 product 服务（8082），不走网关（/internal/cache 为内部接口，网关一律 404）
# 用法：powershell -ExecutionPolicy Bypass -File scripts/smoke-test-cache.ps1
# =============================================================
$svc = 'http://127.0.0.1:8082'
$outDir = Join-Path $env:TEMP 'eshop-cache-smoke'
Remove-Item $outDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$mysql = 'D:\MySQL\MySQL Server 8.0\bin\mysql.exe'

$pass = 0; $fail = 0
function Check {
    param([string]$name, [bool]$ok, [string]$detail)
    if ($ok) { $script:pass++; Write-Host ("[PASS] " + $name) -ForegroundColor Green }
    else { $script:fail++; Write-Host ("[FAIL] " + $name + "  => " + $detail) -ForegroundColor Red }
}

function Send {
    param([string]$name, [string]$method, [string]$url)
    $bodyFile = Join-Path $outDir "$name.body.json"
    $a = @('-s', '-o', $bodyFile, '-w', '%{http_code}', '-X', $method, $url)
    $code = & curl.exe @a
    return $code
}

function Resp {
    param([string]$name)
    return (Get-Content (Join-Path $outDir "$name.body.json") -Raw -Encoding UTF8 | ConvertFrom-Json)
}

# ---------- 1. 健康检查 ----------
Write-Host '==== 1. 健康检查 ===='
$code = Send 'h1-health' 'GET' "$svc/health"
Check '1.1 product 健康' ($code -eq '200') "http=$code"

# ---------- 2. 基线：手动预热 ----------
Write-Host '==== 2. 缓存预热 ===='
$code = Send 'p1-preheat' 'POST' "$svc/internal/cache/preheat"
$r = Resp 'p1-preheat'
Check '2.1 预热接口 200' ($code -eq '200' -and $r.code -eq 200) "http=$code code=$($r.code)"
Check '2.2 预热摘要包含完成统计' ($r.data -like '*预热完成*') "data=$($r.data)"

# ---------- 3. 详情缓存 Cache Aside：删除→回源重建→TTL 抖动 ----------
Write-Host '==== 3. 详情缓存（Cache Aside） ===='
$null = Send 'e1-evict-off' 'POST' "$svc/internal/cache/spu/1/evict?doubleDelete=false"
$null = Send 'i1-inspect' 'GET' "$svc/internal/cache/spu/1"
$r = Resp 'i1-inspect'
Check '3.1 evict 后缓存消失' ($r.data.exists -eq $false) "exists=$($r.data.exists)"

$code = Send 'g1-detail' 'GET' "$svc/spu/1"
$r = Resp 'g1-detail'
Check '3.2 详情接口 200' ($code -eq '200' -and $r.code -eq 200) "http=$code code=$($r.code)"

$null = Send 'i2-inspect' 'GET' "$svc/internal/cache/spu/1"
$r = Resp 'i2-inspect'
$ttlOk = ($r.data.exists -eq $true) -and ($r.data.ttlMillis -gt 1750000) -and ($r.data.ttlMillis -lt 2200000)
Check '3.3 回源后写入缓存且 TTL 含随机抖动' $ttlOk "exists=$($r.data.exists) ttl=$($r.data.ttlMillis)"
Check '3.4 缓存内容非空值标记' ($r.data.nullMarker -eq $false) "nullMarker=$($r.data.nullMarker)"

# ---------- 4. 防穿透（一）：布隆过滤器拦截不存在的 ID ----------
Write-Host '==== 4. 防穿透：布隆拦截 ===='
$code = Send 'g2-spu-absent' 'GET' "$svc/spu/99999999"
$r = Resp 'g2-spu-absent'
Check '4.1 不存在 ID 返回 2001' ($r.code -eq 2001) "code=$($r.code)"
$null = Send 'i3-inspect-absent' 'GET' "$svc/internal/cache/spu/99999999"
$r = Resp 'i3-inspect-absent'
Check '4.2 布隆拦截不落缓存' ($r.data.exists -eq $false) "exists=$($r.data.exists)"

# ---------- 5. 防穿透（二）：空值缓存（在布隆中但已下架） ----------
Write-Host '==== 5. 防穿透：空值缓存 ===='
& $mysql -uroot -p123456 -e "UPDATE shop_product.t_spu SET status=0 WHERE id=1;" 2>$null
$null = Send 'e2-evict-off' 'POST' "$svc/internal/cache/spu/1/evict?doubleDelete=false"

$code = Send 'g3-offline' 'GET' "$svc/spu/1"
$r = Resp 'g3-offline'
Check '5.1 下架后返回 2001' ($r.code -eq 2001) "code=$($r.code)"

$null = Send 'i4-inspect-null' 'GET' "$svc/internal/cache/spu/1"
$r = Resp 'i4-inspect-null'
Check '5.2 空值缓存已写入' (($r.data.exists -eq $true) -and ($r.data.nullMarker -eq $true)) "exists=$($r.data.exists) nullMarker=$($r.data.nullMarker)"

$code = Send 'g4-offline-again' 'GET' "$svc/spu/1"
$r = Resp 'g4-offline-again'
Check '5.3 二次请求命中空值缓存仍 2001' ($r.code -eq 2001) "code=$($r.code)"

& $mysql -uroot -p123456 -e "UPDATE shop_product.t_spu SET status=1 WHERE id=1;" 2>$null
$null = Send 'e3-evict-off' 'POST' "$svc/internal/cache/spu/1/evict?doubleDelete=false"
$code = Send 'g5-restored' 'GET' "$svc/spu/1"
$r = Resp 'g5-restored'
Check '5.4 恢复上架后接口正常' ($r.code -eq 200) "code=$($r.code)"

# ---------- 6. 延迟双删 ----------
Write-Host '==== 6. 延迟双删 ===='
$null = Send 'e4-evict-double' 'POST' "$svc/internal/cache/spu/1/evict?doubleDelete=true"
$null = Send 'i5-inspect-right-after' 'GET' "$svc/internal/cache/spu/1"
$r = Resp 'i5-inspect-right-after'
Check '6.1 evict 后缓存立即消失' ($r.data.exists -eq $false) "exists=$($r.data.exists)"

$null = Send 'g6-rebuild' 'GET' "$svc/spu/1"
$null = Send 'i6-inspect-rebuilt' 'GET' "$svc/internal/cache/spu/1"
$r = Resp 'i6-inspect-rebuilt'
Check '6.2 期间读请求回填缓存' ($r.data.exists -eq $true) "exists=$($r.data.exists)"

Start-Sleep -Milliseconds 2500
$null = Send 'i7-inspect-delayed' 'GET' "$svc/internal/cache/spu/1"
$r = Resp 'i7-inspect-delayed'
Check '6.3 延迟第二次删除已执行（回填缓存被清除）' ($r.data.exists -eq $false) "exists=$($r.data.exists)"

$code = Send 'g7-final' 'GET' "$svc/spu/1"
$r = Resp 'g7-final'
Check '6.4 再次请求正常重建' ($r.code -eq 200) "code=$($r.code)"

# ---------- 7. 分类列表缓存 ----------
Write-Host '==== 7. 分类列表缓存 ===='
$code = Send 'k1-categories' 'GET' "$svc/categories"
$r = Resp 'k1-categories'
Check '7.1 分类接口 200' ($code -eq '200' -and $r.code -eq 200) "http=$code code=$($r.code)"

Write-Host ''
Write-Host ("==== CACHE SMOKE DONE: PASS {0} / FAIL {1} ====" -f $pass, $fail)
