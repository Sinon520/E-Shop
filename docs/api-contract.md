# E-Shop 前后端接口契约（v1.1）

> 状态：**已实现（P1 后端全部就绪）** ｜ 更新：2026-10-14
> 范围：认证 / 商品 / 库存 / 购物车（对应已定稿原型 4 个页面）+ 收货地址 / 订单 / 支付（第二批已开发）
> 原则：**契约先行**——本文档是前后端开发的唯一约定；代码完成后各服务暴露 Knife4j 在线文档，与本文档互为对照
> 在线文档：Knife4j 直连各服务端口访问（网关不暴露），如 http://127.0.0.1:8081/doc.html

---

## 一、通用约定

### 1.1 入口与路由

- 前端所有请求统一走网关：本地开发 `http://127.0.0.1:9000`
- 网关路由规则：`/api/{service}/**` → `lb://eshop-{service}`（转发时自动去掉 `/api/{service}` 前缀）

| 前缀 | 服务 | 端口 |
|---|---|---|
| `/api/user/**` | eshop-user | 8081 |
| `/api/product/**` | eshop-product | 8082 |
| `/api/stock/**` | eshop-stock | 8083 |
| `/api/cart/**` | eshop-cart | 8084 |
| `/api/order/**` | eshop-order（第二批） | 8085 |
| `/api/pay/**` | eshop-pay（第二批） | 8086 |

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
| 403 | 没有相关权限 | |
| 404 | 资源不存在 | |
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
- **免鉴权白名单**（网关放行）：
  - `POST /api/user/auth/register`、`POST /api/user/auth/login`
  - 商品浏览类：`GET /api/product/**`、`GET /api/stock/**`
- token 有效期：**2 小时**（脚本版；refresh token 续期机制列入第二批）

### 1.5 分页约定

请求：`page`（页码，**从 1 开始**）、`size`（每页条数，默认 12，最大 100）

响应 data 结构（`PageResult`，将加入 eshop-common）：

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

## 二、认证与用户域 `/api/user`

### 2.1 注册

`POST /api/user/auth/register`

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

`POST /api/user/auth/login`

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

> 地址簿（增删改查）列入第二批。

---

## 三、商品域 `/api/product`

### 3.1 分类列表

`GET /api/product/categories`

```json
// 响应 data（平铺列表，前端按 parentId 组装或平铺展示）
[
  { "id": 1, "parentId": 0, "name": "数码影音", "level": 1, "icon": "", "sort": 0 },
  { "id": 2, "parentId": 0, "name": "智能穿戴", "level": 1, "icon": "", "sort": 1 }
]
```

> 原型首页分类入口中的「全部商品」由前端本地追加，不属于真实分类。

### 3.2 商品列表（分页 / 筛选 / 排序 / 搜索）

`GET /api/product/spu/page`

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

### 3.3 商品详情

`GET /api/product/spu/{id}`

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

失败：`2001 商品不存在或已下架`

---

## 四、库存域 `/api/stock`

### 4.1 查询 SKU 可用库存

`GET /api/stock/{skuId}`

```json
// 响应 data
{ "skuId": 11, "available": 236 }
```

- 详情页：切换规格时查询，展示「库存 N 件」；加购数量上限 = available
- 失败：`3001 SKU 不存在`

---

## 五、购物车域 `/api/cart`

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

## 六、契约发现的数据库变更（已执行 · 2026-10-14）

| 变更 | 说明 |
|---|---|
| `t_sku` 增加 `market_price DECIMAL(10,2) NOT NULL DEFAULT 0` | 划线价（市场价），支撑列表/详情的「划线价」展示；DDL 已更新至 `sql/02`，本地库已 ALTER |

---

## 七、收货地址 `/api/user/addresses`（全部需登录）

### 7.1 地址列表

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

### 7.2 新增地址

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

### 7.3 修改地址

`PUT /api/user/addresses/{id}` ｜ 请求体同 7.2

### 7.4 删除地址

`DELETE /api/user/addresses/{id}`（逻辑删除）

失败：`1004 收货地址不存在`（不存在或非本人）

---

## 八、订单 `/api/order`（全部需登录）

### 8.1 创建订单

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
| 购物车 | 下单成功后自动移除已下单 SKU |
| 订单号格式 | `ES` + 时间戳 + 随机数，共 22 位 |

失败：`1004 收货地址不存在` / `3001 SKU 不存在或已下架` / `3002 库存不足`

### 8.2 订单分页

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

### 8.3 订单详情

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

### 8.4 取消订单

`PUT /api/order/{orderNo}/cancel` ｜ 仅「待支付」可取消；成功后回补库存（可售 ← 锁定）

失败：`5002 订单状态不允许该操作`

> 超时关单（待支付 30 分钟自动关闭，状态 5）由 **P2 RocketMQ 延迟消息**实现，本期未启用。

---

## 九、支付 `/api/pay`（需登录）

### 9.1 创建支付单

`POST /api/pay/create`

```json
// 请求
{ "orderNo": "ES20261014193000123456" }
// 响应 data
{ "paymentNo": "PAY20261014193000123456", "orderNo": "ES20261014193000123456", "amount": 1297.00, "status": 0 }
```

- 支付单 1:1 订单；同一订单重复创建**幂等复用**同一支付单（重开收银台）；支付失败后重置为待支付可重新发起
- 失败：`5001 订单不存在`（含非本人）/ `5002 订单状态不允许该操作`（非待支付）

### 9.2 模拟支付回调

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

## 十、服务内部接口（服务间调用，不经网关）

| 接口 | 提供方 | 调用方 | 说明 |
|---|---|---|---|
| `POST /internal/sku/batch`（body: `[skuId,...]`） | eshop-product | eshop-cart / eshop-order | 批量查询 SKU 信息（名称/规格文案/图/实时价），只返回在架 SKU |
| `GET /internal/addresses/{id}` | eshop-user | eshop-order | 收货地址快照（含 userId，供归属校验） |
| `POST /internal/stock/lock` | eshop-stock | eshop-order | 锁定库存（可售 → 锁定）；不足抛 3002 |
| `POST /internal/stock/confirm` | eshop-stock | eshop-order（支付成功后） | 确认扣减（锁定 → 已售） |
| `POST /internal/stock/restore` | eshop-stock | eshop-order（取消/失败补偿） | 回补库存（锁定 → 可售） |
| `GET /internal/order/{orderNo}` | eshop-order | eshop-pay | 订单支付信息（归属/状态/金额） |
| `POST /internal/order/{orderNo}/mark-paid` | eshop-order | eshop-pay（回调触发） | 标记订单已支付（幂等） |

> 三个库存接口共用请求体：`{ "orderNo": "...", "items": [ { "skuId": 11, "quantity": 2 } ] }`，
> 按 `(orderNo, skuId, type)` 幂等（库存流水去重），重复调用安全。
>
> **暴露面管控**：内部接口统一 `/internal/` 前缀，网关对该前缀一律返回 404（HTTP 404 + 统一 JSON），
> 客户端无法经网关访问；服务间调用走 Nacos 服务发现直连。内部接口不属于前后端契约，此处仅作登记。

---

## 十一、待办（未实现，规划中）

| 项 | 说明 |
|---|---|
| refresh token 续期 | `POST /api/user/auth/refresh`；access 2h 已满足演示，前端接入时一并实现 |
| 超时关单 | P2：RocketMQ 延迟消息 30 分钟未支付自动关单 + 回补库存（状态 5） |
| 发货 / 确认收货 | P3 管理后台（状态 2 → 3 流转） |

---

## 附：与原型页面的对应关系

| 原型页面 | 使用的接口 |
|---|---|
| 首页 | 3.1 分类列表、3.2 商品列表（sort=sales&size=8） |
| 登录/注册（toast 占位） | 2.1 注册、2.2 登录 |
| 商品列表页 | 3.1 分类、3.2 列表（筛选/排序/搜索/分页） |
| 商品详情页 | 3.3 详情、4.1 库存、5.2 加购 |
| 购物车页 | 5.1 列表、5.3 数量、5.4/5.5 勾选、5.6/5.7 删除 |
| 顶部角标 | 5.1 列表（前端累计 quantity） |

> 结算 / 收银台 / 订单列表页为原型范围之外的新增页面（交易闭环），分别使用 七（地址）、八（订单）、九（支付）接口；地址簿同时供个人中心使用。
