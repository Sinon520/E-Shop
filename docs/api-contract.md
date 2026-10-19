# E-Shop 前后端接口契约（v2.0）

> 状态：**全部已实现**（P1 交易闭环 + P2 技术亮点 + P3 管理端后端）
> 更新：2026-10-16 ｜ v1.1 → v2.0 变更：新增管理后台（§九 · 19 接口）、商品搜索（§3.3）、内部接口扩充至 13 个、超时关单/发货等 P2/P3 能力登记
> 覆盖范围：**C 端 25 个业务接口 + 管理端 19 个接口 + 服务内部 13 个接口 + 6 个健康检查 = 63 个**
> 原则：**契约先行**——本文档是前后端开发的唯一约定；代码完成后各服务暴露 Knife4j 在线文档，与本文档互为对照
> 全量接口一览（一页速查）：见 **附录 A**；在线文档：见 **附录 D**

---

## 一、通用约定

### 1.1 入口与路由

- 前端所有请求统一走网关：本地开发 `http://127.0.0.1:9000`
- **C 端路由**：`/api/{service}/**` → `lb://eshop-{service}`（转发时自动去掉 `/api/{service}` 前缀）

| 前缀 | 服务 | 端口 |
|---|---|---|
| `/api/user/**` | eshop-user | 8081 |
| `/api/product/**` | eshop-product | 8082 |
| `/api/stock/**` | eshop-stock | 8083 |
| `/api/cart/**` | eshop-cart | 8084 |
| `/api/order/**` | eshop-order | 8085 |
| `/api/pay/**` | eshop-pay | 8086 |

- **管理端路由**（P3）：`/api/admin/{domain}/**` → 各服务 `/admin/{domain}/**`（RewritePath 保留 `/admin` 前缀）

| 前缀 | 服务 |
|---|---|
| `/api/admin/auth/**`、`/api/admin/user/**` | eshop-user |
| `/api/admin/product/**` | eshop-product |
| `/api/admin/stock/**` | eshop-stock |
| `/api/admin/order/**`、`/api/admin/dashboard` | eshop-order |

- **内部接口**：服务间经 Nacos 直连调用，统一 `/internal/` 前缀；网关对含 `/internal/` 的路径**一律返回 404**（不存在路由暴露，详见 §十）

### 1.2 统一响应格式

所有接口（含错误）统一返回：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { }
}
```

| 字段 | 说明 |
|---|---|
| code | 200 成功；其他为失败（见 1.3） |
| message | 提示信息，前端可直接展示 |
| data | 业务数据；无数据时为 null |

### 1.3 错误码

**HTTP 层错误码**（对应 `eshop-common` 的 ResultCode）：

| code | 含义 | 典型场景 |
|---|---|---|
| 200 | 操作成功 | |
| 400 | 参数校验失败 | 缺参数、格式错误 |
| 401 | 未登录或登录已过期 | token 缺失/无效/过期 |
| 403 | 没有相关权限 | 非 admin token 访问管理端接口 |
| 404 | 资源不存在 | 含经网关访问 `/internal/**` |
| 405 | 请求方式不支持 | 客户端调用方法错误（如 PUT 调了 GET 接口） |
| 500 | 系统异常 / 通用业务失败 | |

**业务错误码（1000 起）**，`BizException` 抛出，前端按 code 精确处理：

| code | 含义 |
|---|---|
| 1001 | 用户名已存在 |
| 1002 | 用户名或密码错误 |
| 1003 | 账号已被禁用 |
| 1004 | 收货地址不存在 |
| 2001 | 商品不存在或已下架 |
| 3001 | SKU 不存在或已下架 |
| 3002 | 库存不足 |
| 4001 | 购物车条目不存在 |
| 5001 | 订单不存在 |
| 5002 | 订单状态不允许该操作 |
| 6001 | 支付单不存在 |

### 1.4 认证机制（JWT）

**一次请求的完整链路**：

```
前端 ──① Authorization: Bearer <token>──▶ 网关 ──② 校验 JWT，解析用户，注入 X-User-Id──▶ 业务服务
```

- 前端：登录成功后保存 `token`（localStorage），此后所有请求头携带 `Authorization: Bearer <token>`
- 网关：统一校验签名与有效期；校验通过后把用户 ID 写入内部请求头 `X-User-Id` 传给下游；校验失败直接返回 401
- 下游服务：信任内网请求头，不重复解析 token
- **网关鉴权失败**：返回 **HTTP 401** + 标准响应体 `{"code":401,"message":"暂未登录或登录已过期","data":null}`（前端在 Axios 错误拦截器中按 code=401 处理跳转登录）
- **防伪造**：网关会先强制剥除客户端传入的 `X-User-Id`，再按校验结果注入，直连服务伪造内部身份头无效

**免鉴权白名单**（网关放行）：

| 类别 | 路径 |
|---|---|
| 用户注册 / 登录 | `POST /api/user/auth/register`、`POST /api/user/auth/login` |
| 管理员登录 | `POST /api/admin/auth/login` |
| 商品浏览类 | `GET /api/product/**` |
| 库存查询类 | `GET /api/stock/**` |
| 健康检查 | `GET /api/{service}/health` |
| CORS 预检 | `OPTIONS /**` |

**管理端鉴权（P3）**：除登录外，`/api/admin/**` 全部要求 **`role=admin` 声明的 JWT**：

- token 缺失/无效/过期 → `401 {"code":401,"message":"暂未登录或登录已过期","data":null}`
- token 有效但 role 非 admin → `403 {"code":403,"message":"无管理员权限","data":null}`（前端管理后台跳登录页）

**内部接口管控**：任何含 `/internal/` 的请求经网关一律 `404 {"code":404,"message":"资源不存在","data":null}`（该判断先于白名单，防止 `GET /api/product/internal/**` 被放行）。

token 有效期：**2 小时**（refresh token 续期机制见附录 E 待办）。

### 1.5 分页约定

请求：`page`（页码，**从 1 开始**）、`size`（每页条数，C 端默认 12 / 管理端默认 10，最大 100）

响应 data 结构（`PageResult`）：

```json
{
  "total": 26,
  "page": 1,
  "size": 12,
  "list": [ ]
}
```

### 1.6 字段规范

| 项 | 约定 |
|---|---|
| 命名 | 极驼峰 camelCase（与前端天然一致） |
| 金额 | number 类型，单位**元**，最多两位小数（如 `399`、`399.5`） |
| 时间 | 字符串 `yyyy-MM-dd HH:mm:ss`（如 `2026-10-14 18:00:00`） |
| ID | number（后端 Long；超大 ID 场景后续评估转字符串） |
| 图片 | URL 字符串；无图时前端展示占位图（对应原型渐变占位块） |
| 请求体 | `POST/PUT` 一律 JSON（`Content-Type: application/json`） |

---

## 二、认证与用户域 `/api/user`（C 端）

### 2.1 注册

`POST /api/user/auth/register` ｜ 免鉴权

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | 是 | 用户名，4–20 位字母数字下划线 |
| password | string | 是 | 密码，6–20 位 |
| phone | string | 否 | 手机号 |

```json
// 响应
{ "code": 200, "message": "操作成功", "data": null }
```

失败示例：`{ "code": 1001, "message": "用户名已存在", "data": null }`

### 2.2 登录

`POST /api/user/auth/login` ｜ 免鉴权

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | 是 | 用户名 |
| password | string | 是 | 密码 |

```json
// 响应 data
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 7200,
  "user": {
    "id": 1001,
    "username": "sinon",
    "nickname": "Sinon",
    "avatar": ""
  }
}
```

失败：`1002 用户名或密码错误` / `1003 账号已被禁用`

### 2.3 当前用户信息

`GET /api/user/profile` ｜ 需要登录

```json
// 响应 data
{
  "id": 1001,
  "username": "sinon",
  "nickname": "Sinon",
  "phone": "138****0000",
  "email": "",
  "avatar": "",
  "createTime": "2026-10-14 17:30:00"
}
```

---

## 三、商品域 `/api/product`（C 端）

### 3.1 分类列表

`GET /api/product/categories` ｜ 免鉴权

```json
// 响应 data（平铺列表，前端按 parentId 组装或平铺展示）
[
  { "id": 1, "parentId": 0, "name": "数码影音", "level": 1, "icon": "", "sort": 0 },
  { "id": 2, "parentId": 0, "name": "智能穿戴", "level": 1, "icon": "", "sort": 1 }
]
```

> 带 Redis 缓存（`product:category:list`），管理端改名/增删时自动失效。
> 原型首页分类入口中的「全部商品」由前端本地追加，不属于真实分类。

### 3.2 商品列表（分页 / 筛选 / 排序 / 搜索）

`GET /api/product/spu/page` ｜ 免鉴权

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page / size | number | 否 | 分页，默认 1 / 12 |
| categoryId | number | 否 | 分类筛选 |
| keyword | string | 否 | 关键词（匹配商品名） |
| sort | string | 否 | `default`（综合，上新优先）/ `sales`（销量↓）/ `priceAsc` / `priceDesc` |

首页「热卖推荐」= `sort=sales&size=8`

```json
// 响应 data.list 元素
{
  "id": 1,
  "name": "无线蓝牙耳机 Pro｜主动降噪 40h 续航",
  "mainImage": "",
  "price": 399,
  "marketPrice": 499,
  "sales": 3240,
  "categoryId": 1,
  "categoryName": "数码影音"
}
```

> `price` = 该 SPU 下最低 SKU 售价；`marketPrice` = 对应 SKU 的划线价（后端聚合计算）。

### 3.3 商品搜索（P4，ES + IK 分词）

`GET /api/product/spu/search` ｜ 免鉴权

参数同 3.2（`sort` 默认=相关度排序）。

- ES 启用时：multi_match 多字段加权（名称 > 分类名等）+ 相关度排序；结果与 3.2 同构
- ES 未启用（`eshop.es.enabled=false`）或查询异常时：**自动降级 DB like 查询**，接口契约不变
- 前端建议：搜索框走本接口，分类筛选走 3.2；两者可互换，响应结构一致

### 3.4 商品详情

`GET /api/product/spu/{id}` ｜ 免鉴权

```json
// 响应 data
{
  "id": 1,
  "name": "无线蓝牙耳机 Pro｜主动降噪 40h 续航",
  "categoryId": 1,
  "categoryName": "数码影音",
  "brandName": "E-Shop 精选",
  "mainImage": "",
  "images": ["", "", ""],
  "detail": "<p>富文本详情…</p>",
  "status": 1,
  "sales": 3240,
  "specs": [
    { "name": "颜色", "values": ["经典黑", "云雾白", "晨雾蓝"] },
    { "name": "版本", "values": ["标准版", "Pro 版"] }
  ],
  "skus": [
    { "id": 11, "skuCode": "ES-2026-BK-STD", "name": "经典黑 / 标准版", "specJson": { "颜色": "经典黑", "版本": "标准版" }, "price": 399, "marketPrice": 499, "image": "", "status": 1 },
    { "id": 12, "skuCode": "ES-2026-BK-PRO", "name": "经典黑 / Pro 版", "specJson": { "颜色": "经典黑", "版本": "Pro 版" }, "price": 499, "marketPrice": 599, "image": "", "status": 1 }
  ]
}
```

| 字段 | 说明 |
|---|---|
| specs | 由后端从 SKU 的 specJson 聚合出规格组，前端直接渲染规格选择器 |
| skus | 前端根据所选规格组合匹配对应 SKU（取 `id` 用于加购与库存查询） |
| detail | 富文本 HTML；原型阶段前端以纯文本降级展示即可 |

> **缓存读路径（P2-1）**：详情走 Cache Aside（1800s + 0~300s 抖动），布隆过滤器防穿透 + 空值标记 + 互斥锁重建；管理端下架/编辑时联动失效（延迟双删）。

失败：`2001 商品不存在或已下架`

---

## 四、库存域 `/api/stock`（C 端）

### 4.1 查询 SKU 可用库存

`GET /api/stock/{skuId}` ｜ 免鉴权

```json
// 响应 data
{ "skuId": 11, "available": 236 }
```

- 详情页：切换规格时查询，展示「库存 N 件」；加购数量上限 = available
- 失败：`3001 SKU 不存在`

> 库存查询优先读 Redis 闸门（P2-4 Lua 预扣），Redis 不可用时自动降级直查 DB。

---

## 五、购物车域 `/api/cart`（C 端）

> 全部接口**需要登录**；服务端按 `X-User-Id` 隔离数据（Redis `cart:{userId}`）
> 列表数据由 cart 服务通过 **OpenFeign 调用商品服务**批量补充（名称/图/价格/规格文案）

### 5.1 购物车列表

`GET /api/cart/list`

```json
// 响应 data
[
  {
    "skuId": 11,
    "spuId": 1,
    "name": "无线蓝牙耳机 Pro｜主动降噪 40h 续航",
    "specText": "经典黑 / 标准版",
    "image": "",
    "price": 399,
    "quantity": 2,
    "checked": true
  }
]
```

> `price` 每次实时取商品服务的最新价（购物车不存价格快照，成交价以下单时为准）。

### 5.2 加入购物车

`POST /api/cart/add`

```json
// 请求
{ "skuId": 11, "quantity": 1 }
// 响应
{ "code": 200, "message": "操作成功", "data": null }
```

- 已存在则累加数量；`quantity` 默认 1，上限 99
- 失败：`3001 SKU 不存在或已下架`

### 5.3 修改数量

`PUT /api/cart/quantity`

```json
{ "skuId": 11, "quantity": 5 }
```

- quantity 取值 1–99（减到 0 请走删除接口）
- 失败：`4001 购物车条目不存在`

### 5.4 勾选 / 取消勾选

`PUT /api/cart/checked`

```json
{ "skuId": 11, "checked": true }
```

### 5.5 全选 / 取消全选

`PUT /api/cart/checked-all`

```json
{ "checked": true }
```

### 5.6 删除单条

`DELETE /api/cart/{skuId}`

### 5.7 批量删除

`POST /api/cart/batch-remove`

```json
{ "skuIds": [11, 12, 13] }
```

> 原型「删除选中」= 前端收集已勾选的 skuId 列表后调用此接口。

---

## 六、收货地址 `/api/user/addresses`（全部需登录）

### 6.1 地址列表

`GET /api/user/addresses`（默认地址置顶，其次按创建倒序）

```json
// 响应 data
[
  {
    "id": 1,
    "receiverName": "张三",
    "receiverPhone": "13800000000",
    "province": "安徽省",
    "city": "合肥市",
    "district": "蜀山区",
    "detailAddress": "创新大道 1 号",
    "isDefault": 1
  }
]
```

### 6.2 新增地址

`POST /api/user/addresses`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| receiverName | string | 是 | 收货人姓名（≤32） |
| receiverPhone | string | 是 | 手机号（11 位） |
| province / city / district | string | 否 | 省 / 市 / 区县（分别 ≤32） |
| detailAddress | string | 是 | 详细地址（≤255） |
| isDefault | boolean | 否 | 设为默认；**首个地址自动默认**，默认地址互斥 |

```json
// 响应 data：新增地址 ID
1
```

### 6.3 修改地址

`PUT /api/user/addresses/{id}` ｜ 请求体同 6.2

### 6.4 删除地址

`DELETE /api/user/addresses/{id}`（逻辑删除）

失败：`1004 收货地址不存在`（不存在或非本人）

---

## 七、订单 `/api/order`（全部需登录）

### 7.1 创建订单

`POST /api/order/create`

```json
// 请求
{
  "addressId": 1,
  "remark": "周末送货",
  "items": [
    { "skuId": 11, "quantity": 2 },
    { "skuId": 12, "quantity": 1 }
  ]
}
// 响应 data
{ "orderNo": "ES20261014193000123456", "payAmount": 1297.00 }
```

| 行为 | 说明 |
|---|---|
| items 来源 | 购物车勾选项（或详情页「立即购买」）由前端整理提交；同一 SKU 重复出现按数量累加（单 SKU 上限 99） |
| 金额口径 | 服务端按商品服务**现价**计算（不信任前端金额）；收货信息与商品信息均为下单时**快照** |
| 库存 | 下单即锁定库存（可售 → 锁定）；锁定失败订单自动置「已取消」并返回失败原因 |
| 事务 | **Seata AT 全局事务**（P2-3）：订单落库 + 库存锁定跨服务原子提交，库存不足=全局回滚零残留 |
| 购物车 | 下单成功后自动移除已下单 SKU |
| 订单号格式 | `ES` + 时间戳 + 随机数，共 22 位 |

失败：`1004 收货地址不存在` / `3001 SKU 不存在或已下架` / `3002 库存不足`

> 下单接口经网关 **Sentinel 限流**（P2-5，默认 QPS=3 演示档）；超限返回 `429 Too Many Requests` + 统一 JSON `{"code":429,"message":"系统繁忙，请稍后再试","data":null}`。

### 7.2 订单分页

`GET /api/order/page`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page / size | number | 否 | 分页，默认 1 / 10 |
| status | number | 否 | 状态过滤：0 待支付 / 1 已支付 / 2 已发货 / 3 已完成 / 4 已取消 / 5 超时关闭 |

```json
// 响应 data（PageResult）list 元素
{
  "orderNo": "ES20261014193000123456",
  "status": 0,
  "statusText": "待支付",
  "totalAmount": 1297.00,
  "payAmount": 1297.00,
  "createTime": "2026-10-14 19:30:00",
  "items": [
    {
      "skuId": 11,
      "spuId": 1,
      "name": "无线蓝牙耳机 Pro｜主动降噪 40h 续航",
      "specText": "经典黑 / 标准版",
      "image": "",
      "price": 399.00,
      "quantity": 2,
      "totalAmount": 798.00
    }
  ]
}
```

### 7.3 订单详情

`GET /api/order/{orderNo}` ｜ 响应 data 在列表字段基础上增加：

```json
{
  "remark": "周末送货",
  "receiverName": "张三",
  "receiverPhone": "13800000000",
  "receiverAddress": "安徽省合肥市蜀山区创新大道 1 号",
  "payTime": ""
}
```

失败：`5001 订单不存在`（含非本人订单，不暴露存在性）

### 7.4 取消订单

`PUT /api/order/{orderNo}/cancel` ｜ 仅「待支付」可取消；成功后回补库存（可售 ← 锁定）

失败：`5002 订单状态不允许该操作`

> **超时关单（P2-2，已启用）**：待支付订单 30 分钟未支付，由 RocketMQ 延迟消息自动关闭（状态 5）+ 回补库存，幂等（已支付/已取消跳过）。

---

## 八、支付 `/api/pay`（需登录）

### 8.1 创建支付单

`POST /api/pay/create`

```json
// 请求
{ "orderNo": "ES20261014193000123456" }
// 响应 data
{ "paymentNo": "PAY20261014193000123456", "orderNo": "ES20261014193000123456", "amount": 1297.00, "status": 0 }
```

- 支付单 1:1 订单；同一订单重复创建**幂等复用**同一支付单（重开收银台）；支付失败后重置为待支付可重新发起
- 失败：`5001 订单不存在`（含非本人）/ `5002 订单状态不允许该操作`（非待支付）

### 8.2 模拟支付回调

`POST /api/pay/callback`

```json
// 请求（模拟第三方支付平台回调；前端演示 outTradeNo 传稳定值 MOCK-{paymentNo} 即可安全重复触发）
{ "paymentNo": "PAY20261014193000123456", "outTradeNo": "MOCK-PAY20261014193000123456", "success": true }
// 响应：{ "code": 200, "message": "操作成功", "data": null }
```

| 行为 | 说明 |
|---|---|
| 幂等（三重防护） | `t_pay_callback_log.uk_out_trade_no` 去重 → 支付单状态机 → 订单侧幂等标记 |
| 成功 | 支付单 → 已支付；同步通知订单（状态 → 已支付）与库存（锁定 → 已售确认扣减） |
| 失败（success=false） | 支付单 → 支付失败 |
| 重复回调 | 直接幂等返回成功，不重复流转 |

失败：`6001 支付单不存在`

---

## 九、管理后台 `/api/admin`（P3）

> 对应原型 `prototype/admin.html`（Vue3 + Element Plus 管理端 `eshop-admin` 前端工程联调依据）
> **鉴权**：本章除 9.1 外全部要求 `role=admin` 的 JWT（401/403 行为见 §1.4）；响应结构与 C 端同构（`Result` / `PageResult`）

### 9.1 管理员登录

`POST /api/admin/auth/login` ｜ **免鉴权**

```json
// 请求
{ "username": "admin", "password": "******" }
// 响应 data
{ "token": "eyJhbGciOiJIUzI1NiJ9...", "expiresIn": 7200, "name": "管理员" }
```

- 账号来源：独立表 `t_admin`（与 C 端用户体系隔离）
- token 携带 `role=admin` 声明；管理端前端本地保存，后续请求携带 `Authorization: Bearer <token>`
- 失败：`1002 用户名或密码错误`

### 9.2 用户管理

#### 9.2.1 用户分页

`GET /api/admin/user/page`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page / size | number | 否 | 分页，默认 1 / 10 |
| keyword | string | 否 | 用户名 / 昵称模糊搜索 |

```json
// 响应 data.list 元素
{
  "id": 1001,
  "username": "sinon",
  "nickname": "Sinon",
  "phone": "138****0000",
  "createTime": "2026-10-14 17:30:00",
  "status": 1
}
```

#### 9.2.2 禁用 / 启用用户

`PUT /api/admin/user/{id}/status`

```json
// 请求：1 正常 / 0 禁用
{ "status": 0 }
```

> 禁用后该用户登录被拒（`1003 账号已被禁用`），已登录 token 在下游校验中仍可能有效至过期（2h）。

### 9.3 商品管理

#### 9.3.1 商品分页（含下架）

`GET /api/admin/product/spu/page`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page / size | number | 否 | 分页，默认 1 / 10 |
| keyword | string | 否 | 商品名模糊搜索 |
| categoryId | number | 否 | 分类筛选 |
| status | number | 否 | 1 上架 / 0 下架（不传=全部） |

```json
// 响应 data.list 元素
{
  "id": 1,
  "name": "无线蓝牙耳机 Pro｜主动降噪 40h 续航",
  "mainImage": "",
  "categoryId": 1,
  "categoryName": "数码影音",
  "price": 399,
  "marketPrice": 499,
  "sales": 3240,
  "status": 1,
  "skuCount": 6
}
```

#### 9.3.2 商品详情（含 SKU 明细，不限状态）

`GET /api/admin/product/spu/{id}` ｜ 响应结构同 §3.4（`SpuDetailVO`），含下架商品

#### 9.3.3 上架 / 下架

`PUT /api/admin/product/spu/{id}/status`

```json
// 请求：1 上架 / 0 下架
{ "status": 0 }
```

> 下架/上架均**联动缓存失效**：删除详情缓存 + 延迟双删 + 重建布隆过滤器（P2-1 链路）。

#### 9.3.4 新增商品（含初始 SKU 列表）

`POST /api/admin/product/spu`

```json
// 请求
{
  "name": "无线蓝牙耳机 Pro",
  "categoryId": 1,
  "brandId": 0,
  "mainImage": "",
  "detail": "<p>富文本详情…</p>",
  "skus": [
    { "name": "经典黑 / 标准版", "price": 399, "marketPrice": 499, "image": "" },
    { "name": "经典黑 / Pro 版", "price": 499, "marketPrice": 599, "image": "" }
  ]
}
// 响应 data：新商品 ID
101
```

- `skus` 仅新增场景生效；`categoryId` 必须为有效分类
- 新增 SKU 后库存侧无记录，需在管理端库存页初始化（调整库存）

#### 9.3.5 编辑商品

`PUT /api/admin/product/spu/{id}` ｜ 请求体同 9.3.4（编辑场景暂不处理 SKU 修改，仅基本信息）

#### 9.3.6 全量重建搜索索引（运维接口）

`POST /api/admin/product/spu/search/reindex`

```json
// 响应 data：同步的文档数
26
```

- 用途：ES 与 DB 数据校准（P4）；ES 未启用（`eshop.es.enabled=false`）时返回业务错误 `400 参数校验失败："ES 搜索未启用…"`

### 9.4 分类管理

#### 9.4.1 全量分类（含停用）

`GET /api/admin/product/category/list`

```json
// 响应 data
[
  { "id": 1, "parentId": 0, "name": "数码影音", "level": 1, "sort": 0, "icon": "", "status": 1, "createTime": "2026-10-14 10:00:00", "updateTime": "2026-10-14 10:00:00" }
]
```

#### 9.4.2 新增分类

`POST /api/admin/product/category`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | string | 是 | 分类名称 |
| parentId | number | 否 | 父级 ID（0 = 一级分类） |
| level | number | 否 | 1 一级 / 2 二级（为空按 parentId 推导） |
| sort | number | 否 | 排序值（越小越靠前） |
| status | number | 否 | 1 启用 / 0 停用 |
| icon | string | 否 | 图标 |

```json
// 响应 data：新分类 ID
5
```

#### 9.4.3 编辑分类

`PUT /api/admin/product/category/{id}` ｜ 请求体同 9.4.2

#### 9.4.4 删除分类

`DELETE /api/admin/product/category/{id}`

> 有子分类或商品引用时拒绝（业务错误）；删除/编辑成功联动 C 端分类缓存失效。

### 9.5 库存管理

#### 9.5.1 库存分页（含 Redis 预扣核对）

`GET /api/admin/stock/page`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page / size | number | 否 | 分页，默认 1 / 10 |
| keyword | string | 否 | SKU/商品名模糊搜索 |
| lowStock | boolean | 否 | true = 只看低库存（available < 10） |

```json
// 响应 data.list 元素
{
  "skuId": 11,
  "spuName": "无线蓝牙耳机 Pro",
  "skuName": "经典黑 / 标准版",
  "price": 399,
  "available": 236,
  "locked": 4,
  "sold": 3240,
  "redisValue": 236,
  "consistent": true
}
```

| 字段 | 说明 |
|---|---|
| available / locked / sold | DB 账本：可售 / 锁定 / 已售 |
| redisValue | Redis 预扣闸门当前值（null = 未预热） |
| consistent | Redis 与 DB 是否一致（不一致时可在库位行点「校准」触发预热重建） |

#### 9.5.2 调整库存

`PUT /api/admin/stock/{skuId}/adjust`

```json
// 请求
{ "mode": "delta", "value": 50, "remark": "补货入库" }
// mode=absolute 时 value 为目标可售量；delta 可负（- 为出库）
```

> 调整同时以 DB 为准同步 Redis 闸门，保证双侧一致。

### 9.6 订单管理

#### 9.6.1 订单分页（全量）

`GET /api/admin/order/page`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page / size | number | 否 | 分页，默认 1 / 10 |
| status | number | 否 | 状态过滤（0–5，同 §7.2） |
| keyword | string | 否 | 订单号模糊搜索 |

> 列表元素结构同 §7.2（`OrderVO`），服务端自动回填下单用户名（内部批量查询）。

#### 9.6.2 订单详情

`GET /api/admin/order/{orderNo}` ｜ 响应结构同 §7.3（不限归属）

#### 9.6.3 发货

`PUT /api/admin/order/{orderNo}/ship` ｜「已支付 → 已发货」状态流转

失败：`5002 订单状态不允许该操作`（非已支付不可发货）

### 9.7 数据看板

`GET /api/admin/dashboard`

```json
// 响应 data
{
  "todaySales": 2586.00,        // 今日成交额（已支付/已发货/已完成）
  "todayOrders": 12,            // 今日订单数（全状态）
  "pendingShip": 3,             // 待发货数（已支付）
  "totalSales": 128600.00,      // 累计成交额
  "statusDist": [ { "status": 0, "cnt": 4 }, { "status": 1, "cnt": 3 } ],
  "trend7d": [ { "d": "2026-10-10", "amount": 1990.00, "orders": 8 } ],
  "hotTop5": [ { "name": "无线蓝牙耳机 Pro", "qty": 156, "amount": 62244.00 } ]
}
```

| 字段 | 说明 |
|---|---|
| statusDist | 订单状态分布（status/cnt），前端映射 0–5 状态文案 |
| trend7d | 近 7 日按天趋势（d/amount/orders），用于折线图 |
| hotTop5 | 热销 TOP5（name/qty/amount），按销量倒序 |

> 全部为订单域本库聚合（订单快照含商品名），不跨服务查询。

---

## 十、服务内部接口（服务间调用，不经网关）

> 统一 `/internal/` 前缀；网关对含该前缀的路径**一律 404**，客户端无法访问；服务间经 Nacos 直连。
> 内部接口不属于前后端契约，此处仅作登记。

| # | 接口 | 提供方 | 调用方 | 说明 |
|---|---|---|---|---|
| 1 | `POST /internal/sku/batch`（body: `[skuId,…]`，query: `includeOffline`） | eshop-product | eshop-cart / eshop-order / eshop-stock（管理端 `includeOffline=true`） | 批量查询 SKU 信息（名称/规格文案/图/实时价）；默认只返回在架 SKU |
| 2 | `GET /internal/cache/spu/{spuId}` | eshop-product | 运维/验收 | 商品详情缓存状态（key / 是否存在 / TTL / 空值标记 / 内容预览） |
| 3 | `POST /internal/cache/spu/{spuId}/evict?doubleDelete=true` | eshop-product | 运维/验收 | 缓存失效 + 延迟双删投递（验证双删链路） |
| 4 | `POST /internal/cache/preheat` | eshop-product | 运维/验收 | 手动预热（布隆过滤器 + 详情缓存） |
| 5 | `GET /internal/addresses/{id}` | eshop-user | eshop-order | 收货地址快照（含 userId，供归属校验） |
| 6 | `POST /internal/users/batch`（body: `[userId,…]`） | eshop-user | eshop-order（管理端订单列表） | 批量查用户基础信息（回填用户名） |
| 7 | `POST /internal/stock/lock` | eshop-stock | eshop-order | 锁定库存（可售 → 锁定）；不足抛 3002 |
| 8 | `POST /internal/stock/confirm` | eshop-stock | eshop-order（支付成功后） | 确认扣减（锁定 → 已售） |
| 9 | `POST /internal/stock/restore` | eshop-stock | eshop-order（取消/关单补偿） | 回补库存（锁定 → 可售） |
| 10 | `GET /internal/stock/redis/{skuId}` | eshop-stock | 运维/验收 | Redis 预扣值 vs DB 账本一致性核对 |
| 11 | `POST /internal/stock/preheat` | eshop-stock | 运维/验收 | Redis 闸门预热（以 DB 为准全量重建 = 对账校准） |
| 12 | `GET /internal/order/{orderNo}` | eshop-order | eshop-pay | 订单支付信息（归属/状态/金额） |
| 13 | `POST /internal/order/{orderNo}/mark-paid` | eshop-order | eshop-pay（回调触发） | 标记订单已支付（幂等） |

> 7–9 三个库存接口共用请求体 `StockOpRequest`：`{ "orderNo": "...", "items": [ { "skuId": 11, "quantity": 2 } ] }`，
> 按 `(orderNo, skuId, type)` 幂等（库存流水去重），重复调用安全。

**消息队列（非 HTTP 接口，登记备查）**：

| Topic | 生产者 | 消费者 | 说明 |
|---|---|---|---|
| `eshop-order-timeout` | eshop-order（下单成功发延迟消息） | eshop-order | 延迟关单（默认 30min，可配），消费端幂等 |

---

## 附录 A：全量接口总览（63 个）

**C 端（25）**

| # | 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|---|
| 1 | POST | /api/user/auth/register | 注册 | 免 |
| 2 | POST | /api/user/auth/login | 登录 | 免 |
| 3 | GET | /api/user/profile | 当前用户信息 | 登录 |
| 4 | GET | /api/user/addresses | 地址列表 | 登录 |
| 5 | POST | /api/user/addresses | 新增地址 | 登录 |
| 6 | PUT | /api/user/addresses/{id} | 修改地址 | 登录 |
| 7 | DELETE | /api/user/addresses/{id} | 删除地址 | 登录 |
| 8 | GET | /api/product/categories | 分类列表 | 免 |
| 9 | GET | /api/product/spu/page | 商品列表 | 免 |
| 10 | GET | /api/product/spu/search | 商品搜索（ES，降级 DB） | 免 |
| 11 | GET | /api/product/spu/{id} | 商品详情 | 免 |
| 12 | GET | /api/stock/{skuId} | SKU 可用库存 | 免 |
| 13 | GET | /api/cart/list | 购物车列表 | 登录 |
| 14 | POST | /api/cart/add | 加入购物车 | 登录 |
| 15 | PUT | /api/cart/quantity | 修改数量 | 登录 |
| 16 | PUT | /api/cart/checked | 勾选/取消勾选 | 登录 |
| 17 | PUT | /api/cart/checked-all | 全选/取消全选 | 登录 |
| 18 | DELETE | /api/cart/{skuId} | 删除单条 | 登录 |
| 19 | POST | /api/cart/batch-remove | 批量删除 | 登录 |
| 20 | POST | /api/order/create | 创建订单（Sentinel 限流） | 登录 |
| 21 | GET | /api/order/page | 订单分页 | 登录 |
| 22 | GET | /api/order/{orderNo} | 订单详情 | 登录 |
| 23 | PUT | /api/order/{orderNo}/cancel | 取消订单 | 登录 |
| 24 | POST | /api/pay/create | 创建支付单 | 登录 |
| 25 | POST | /api/pay/callback | 模拟支付回调 | 登录 |

**管理端（19）**

| # | 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|---|
| 1 | POST | /api/admin/auth/login | 管理员登录 | 免 |
| 2 | GET | /api/admin/user/page | 用户分页 | admin |
| 3 | PUT | /api/admin/user/{id}/status | 禁用/启用用户 | admin |
| 4 | GET | /api/admin/product/spu/page | 商品分页（含下架） | admin |
| 5 | GET | /api/admin/product/spu/{id} | 商品详情（含 SKU） | admin |
| 6 | PUT | /api/admin/product/spu/{id}/status | 上架/下架（联动缓存） | admin |
| 7 | POST | /api/admin/product/spu | 新增商品 | admin |
| 8 | PUT | /api/admin/product/spu/{id} | 编辑商品 | admin |
| 9 | POST | /api/admin/product/spu/search/reindex | 重建搜索索引 | admin |
| 10 | GET | /api/admin/product/category/list | 全量分类 | admin |
| 11 | POST | /api/admin/product/category | 新增分类 | admin |
| 12 | PUT | /api/admin/product/category/{id} | 编辑分类 | admin |
| 13 | DELETE | /api/admin/product/category/{id} | 删除分类 | admin |
| 14 | GET | /api/admin/stock/page | 库存分页（含 Redis 核对） | admin |
| 15 | PUT | /api/admin/stock/{skuId}/adjust | 调整库存 | admin |
| 16 | GET | /api/admin/order/page | 订单分页（全量） | admin |
| 17 | GET | /api/admin/order/{orderNo} | 订单详情 | admin |
| 18 | PUT | /api/admin/order/{orderNo}/ship | 发货 | admin |
| 19 | GET | /api/admin/dashboard | 数据看板 | admin |

**内部接口（13）**：见 §十 表格。

**健康检查（6）**：`GET /api/{user|product|stock|cart|order|pay}/health`（免鉴权，返回服务名/端口/时间）。

---

## 附录 B：数据库变更记录（历史）

| 日期 | 变更 | 说明 |
|---|---|---|
| 2026-10-14 | `t_sku` 增加 `market_price DECIMAL(10,2) NOT NULL DEFAULT 0` | 划线价（市场价），支撑列表/详情的「划线价」展示；DDL 已更新至 `sql/02` |
| 2026-10-14 | 新增 `t_admin`（P3） | 管理后台独立账号表（与 C 端用户隔离） |

---

## 附录 C：页面 ↔ 接口映射

**C 端（原型 `prototype/index.html`，实现于 `eshop-web`）**

| 页面 | 使用的接口 |
|---|---|
| 首页 | 3.1 分类列表、3.2 商品列表（sort=sales&size=8） |
| 登录/注册 | 2.1 注册、2.2 登录 |
| 商品列表页 | 3.1 分类、3.2 列表（筛选/排序）、3.3 搜索（分页） |
| 商品详情页 | 3.4 详情、4.1 库存、5.2 加购 |
| 购物车页 | 5.1 列表、5.3 数量、5.4/5.5 勾选、5.6/5.7 删除 |
| 结算页 | 6.1 地址列表、6.2 地址新增、7.1 创建订单 |
| 收银台 | 8.1 创建支付单、8.2 模拟回调 |
| 订单列表 / 详情 | 7.2 分页、7.3 详情、7.4 取消、8.1 重新支付 |
| 个人中心 | 2.3 用户信息、6.x 地址簿 |
| 顶部角标 | 5.1 列表（前端累计 quantity） |

**管理端（原型 `prototype/admin.html`，实现于 `eshop-admin`）**

| 页面 | 使用的接口 |
|---|---|
| 登录页 | 9.1 管理员登录 |
| 数据看板 | 9.7 看板；9.6.1 订单分页（待发货卡片跳转） |
| 商品管理（列表/编辑/新增） | 9.3.1–9.3.5；分类下拉 = 9.4.1 |
| 分类管理 | 9.4.1–9.4.4 |
| 库存管理 | 9.5.1–9.5.2 |
| 订单管理 | 9.6.1–9.6.3 |
| 用户管理 | 9.2.1–9.2.2 |

---

## 附录 D：Knife4j 在线文档

各服务直连端口访问（网关不暴露），与本文档互为对照：

| 服务 | 地址 |
|---|---|
| eshop-user | http://127.0.0.1:8081/doc.html |
| eshop-product | http://127.0.0.1:8082/doc.html |
| eshop-stock | http://127.0.0.1:8083/doc.html |
| eshop-cart | http://127.0.0.1:8084/doc.html |
| eshop-order | http://127.0.0.1:8085/doc.html |
| eshop-pay | http://127.0.0.1:8086/doc.html |

---

## 附录 E：待办（未实现，规划中）

| 项 | 说明 |
|---|---|
| refresh token 续期 | `POST /api/user/auth/refresh`；access 2h 已满足演示，前端接入时一并实现 |
| 确认收货 | C 端「已发货 → 已完成（2 → 3）」流转接口；管理端发货（1 → 2）已实现 |
| 管理端订单趋势图数据补全 | `trend7d` 目前按有单日期返回，前端折线图需自行补齐空日期为 0 |
