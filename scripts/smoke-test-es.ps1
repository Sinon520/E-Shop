# =============================================================
# E-Shop 商品搜索冒烟测试（P4：Elasticsearch + IK 分词）
# 前置：nacos + mysql + redis + elasticsearch(9200) up；
#       eshop-product(8082) + eshop-gateway(9000) running
# 说明：搜索/列表走网关 9000（与 C 端真实链路一致）；索引直查 9200；
#       中文关键词用 URL 编码常量，规避控制台 GBK 编码差异
# 用法：powershell -ExecutionPolicy Bypass -File scripts/smoke-test-es.ps1
# 降级验证（手动）：停 ES 后重启 product，/spu/search 仍 200 并返回 DB like 结果
# =============================================================
$gw = 'http://127.0.0.1:9000'
$es = 'http://127.0.0.1:9200'
$outDir = Join-Path $env:TEMP 'eshop-smoke-es'
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

# 中文关键词 URL 编码（UTF-8）：蓝牙耳机 / 耳机
$kwHeadset = '%E8%93%9D%E7%89%99%E8%80%B3%E6%9C%BA'
$kwEarphone = '%E8%80%B3%E6%9C%BA'

# ---------- 1. 健康检查 ----------
Write-Host '==== 1. 健康检查 ===='
$code = Send 'h1-product' 'GET' 'http://127.0.0.1:8082/health'
Check '1.1 product 健康' ($code -eq '200') "http=$code"
$esInfo = Invoke-RestMethod "$es/" -TimeoutSec 5
Check '1.2 ES 集群可达（cluster_name=eshop）' ($esInfo.cluster_name -eq 'eshop') "cluster=$($esInfo.cluster_name)"
$ikBody = '{"analyzer":"ik_smart","text":"无线蓝牙耳机"}'
$ikFile = Join-Path $outDir 'ik.req.json'
[System.IO.File]::WriteAllText($ikFile, $ikBody, [System.Text.UTF8Encoding]::new($false))
$null = & curl.exe -s -o (Join-Path $outDir 'ik.body.json') -X POST "$es/_analyze" -H 'Content-Type: application/json' --data-binary "@$ikFile"
$ik = Resp 'ik'
$ikTokens = @($ik.tokens | ForEach-Object { $_.token })
Check '1.3 IK 分词生效（无线/蓝牙/耳机）' (($ikTokens -contains '蓝牙') -and ($ikTokens -contains '耳机')) "tokens=$($ikTokens -join ',')"

# ---------- 2. 管理端登录 + 全量重建 ----------
Write-Host '==== 2. 全量重建 ===='
$code = Send 'b1-login' 'POST' "$gw/api/admin/auth/login" $null '{"username":"admin","password":"admin123"}'
$adminToken = (Resp 'b1-login').data.token
Check '2.1 admin 登录' ($code -eq '200' -and $adminToken) "http=$code"
$adminHdr = @{ 'Authorization' = "Bearer $adminToken" }

$code = Send 'b2-reindex' 'POST' "$gw/api/admin/product/spu/search/reindex" $adminHdr $null
$r = Resp 'b2-reindex'
Check '2.2 reindex 接口 200' ($code -eq '200' -and $r.code -eq 200) "http=$code code=$($r.code)"
Start-Sleep -Milliseconds 1800   # ES 1s 自动 refresh，稍候再断言可见性
$reindexed = [long]$r.data
$null = & curl.exe -s -o (Join-Path $outDir 'count.body.json') "$es/eshop_spu/_count"
$esCount = (Resp 'count').count
Check '2.3 ES 文档数与 reindex 返回一致' ($esCount -eq $reindexed) "reindex=$reindexed es=$esCount"

# ---------- 3. 中文分词搜索（网关链路） ----------
Write-Host '==== 3. 中文搜索 ===='
$code = Send 'c1-search' 'GET' "$gw/api/product/spu/search?keyword=$kwHeadset&size=12"
$r = Resp 'c1-search'
Check '3.1 搜索接口 200' ($code -eq '200' -and $r.code -eq 200) "http=$code code=$($r.code)"
Check '3.2 「蓝牙耳机」命中 ≥3' ([long]$r.data.total -ge 3) "total=$($r.data.total)"
$top1 = $r.data.list[0]
Check '3.3 name^3 加权：top1 为「无线蓝牙耳机 Pro」' ($top1.id -eq 1) "top1.id=$($top1.id) name=$($top1.name)"

# 相关度排序下分类名/品牌名参与召回（ categoryName^2 / brandName ）
$code = Send 'c3-detail-kw' 'GET' "$gw/api/product/spu/search?keyword=ESSEARCHWORD&size=12"
$r = Resp 'c3-detail-kw'
Check '3.4 detail 正文参与检索（新建前未命中为 0）' ([long]$r.data.total -eq 0) "total=$($r.data.total)"

# ---------- 4. 排序 ----------
Write-Host '==== 4. 排序 ===='
$code = Send 'd1-priceasc' 'GET' "$gw/api/product/spu/search?keyword=$kwHeadset&sort=priceAsc&size=12"
$r = Resp 'd1-priceasc'
$prices = @($r.data.list | ForEach-Object { [double]$_.price })
$sorted = $true
for ($i = 1; $i -lt $prices.Count; $i++) { if ($prices[$i] -lt $prices[$i - 1]) { $sorted = $false } }
Check '4.1 priceAsc 单调不减' ($sorted -and $prices.Count -ge 2) "prices=$($prices -join ',')"

$code = Send 'd2-sales' 'GET' "$gw/api/product/spu/search?keyword=$kwEarphone&sort=sales&size=12"
$r = Resp 'd2-sales'
$salesList = @($r.data.list | ForEach-Object { [int]$_.sales })
$sorted = $true
for ($i = 1; $i -lt $salesList.Count; $i++) { if ($salesList[$i] -gt $salesList[$i - 1]) { $sorted = $false } }
Check '4.2 sales 降序' ($sorted -and $salesList.Count -ge 2) "sales=$($salesList -join ',')"

# ---------- 5. 分类过滤 ----------
Write-Host '==== 5. 分类过滤 ===='
$code = Send 'e1-byname' 'GET' "$gw/api/product/spu/search?keyword=$kwEarphone&size=12"
$r = Resp 'e1-byname'
$catId = $r.data.list[0].categoryId
$code = Send 'e2-filter' 'GET' "$gw/api/product/spu/search?keyword=$kwEarphone&categoryId=$catId&size=12"
$r = Resp 'e2-filter'
$allMatch = @($r.data.list | Where-Object { $_.categoryId -ne $catId }).Count -eq 0
Check '5.1 categoryId 过滤后结果全属该分类' ($allMatch -and [long]$r.data.total -ge 1) "cat=$catId total=$($r.data.total)"

# ---------- 6. 上下架实时同步 ----------
Write-Host '==== 6. 上下架同步 ===='
$code = Send 'f1-off' 'PUT' "$gw/api/admin/product/spu/4/status" $adminHdr '{"status":0}'
Check '6.1 下架接口 200' ($code -eq '200') "http=$code"
Start-Sleep -Milliseconds 800
$doc = Invoke-RestMethod "$es/eshop_spu/_doc/4"
Check '6.2 ES 文档 status 同步为 0' ($doc._source.status -eq 0) "status=$($doc._source.status)"
$code = Send 'f3-search-off' 'GET' "$gw/api/product/spu/search?keyword=$kwHeadset&size=12"
$r = Resp 'f3-search-off'
$hidden = @($r.data.list | Where-Object { $_.id -eq 4 }).Count -eq 0
Check '6.3 下架商品不再出现在搜索结果' $hidden "total=$($r.data.total)"
$code = Send 'f4-on' 'PUT' "$gw/api/admin/product/spu/4/status" $adminHdr '{"status":1}'
Start-Sleep -Milliseconds 800
$doc = Invoke-RestMethod "$es/eshop_spu/_doc/4"
Check '6.4 重新上架后 ES status 恢复 1' ($code -eq '200' -and $doc._source.status -eq 1) "http=$code status=$($doc._source.status)"

# ---------- 7. 新增商品同步 + detail 检索 ----------
Write-Host '==== 7. 新增同步 ===='
$newBody = '{"name":"ES Smoke Test Product","categoryId":1,"mainImage":"","detail":"<p>uniquedetailword2026 marks this doc</p>","skus":[{"name":"标准版","price":99.00}]}'
$code = Send 'g1-create' 'POST' "$gw/api/admin/product/spu" $adminHdr $newBody
$newId = (Resp 'g1-create').data
Check '7.1 新增商品 200' ($code -eq '200' -and $newId) "http=$code id=$newId"
Start-Sleep -Milliseconds 800
$doc = Invoke-RestMethod "$es/eshop_spu/_doc/$newId"
Check '7.2 新商品（默认下架）文档已同步 status=0' ($doc._source.status -eq 0) "status=$($doc._source.status)"
$code = Send 'g3-search-new' 'GET' "$gw/api/product/spu/search?keyword=uniquedetailword2026&size=12"
$r = Resp 'g3-search-new'
$hiddenNew = @($r.data.list | Where-Object { $_.id -eq $newId }).Count -eq 0
Check '7.3 下架的新商品不可见（对历史残留免疫）' $hiddenNew "total=$($r.data.total)"
$code = Send 'g4-puton' 'PUT' "$gw/api/admin/product/spu/$newId/status" $adminHdr '{"status":1}'
Start-Sleep -Milliseconds 1800   # 等 ES refresh 与同步完成
$code = Send 'g5-search-new2' 'GET' "$gw/api/product/spu/search?keyword=uniquedetailword2026&size=12"
$r = Resp 'g5-search-new2'
$found = @($r.data.list | Where-Object { $_.id -eq $newId }).Count -ge 1
Check '7.4 上架后 detail 正文词可检索' ($found -and $code -eq '200') "total=$($r.data.total) found=$found"

Write-Host ''
Write-Host "==== 结果：$pass 通过 / $fail 失败 ===="
if ($fail -eq 0) { exit 0 } else { exit 1 }
