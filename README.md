# E-Shop 电商商城

基于 Spring Cloud 微服务架构的 B2C 自营商城，覆盖「浏览 → 加购 → 下单 → 支付 → 履约」完整交易链路，并配套运营管理后台（含数据看板、商品/库存/订单管理）与 ES 中文搜索。

> **技术栈**：JDK 21 · Spring Boot 3.5 · Spring Cloud 2025.0 · Spring Cloud Alibaba 2025.0 · Nacos 3 · MySQL 8 · Redis（Redisson）· RocketMQ · Seata · Sentinel · Elasticsearch 8 + IK 分词 · Vue 3 + Vite + Pinia（C 端）/ Element Plus + ECharts（管理端）
>
> **当前状态**：**核心链路 + 技术亮点 + 管理后台 + ES 搜索全部完成**，全链路可端到端演示；仅剩 Docker Compose 编排未做。

## 技术亮点

| # | 亮点 | 说明 |
|---|---|---|
| 1 | 商品缓存三件套 | Cache Aside + 布隆过滤器 + 空值缓存 + 互斥锁重建 + **ZSET 自实现延迟队列的延迟双删**（RDelayedQueue 已废弃、RReliableQueue 需 PRO 授权） |
| 2 | RocketMQ 超时关单 | 下单发延迟消息，到点自动关单 + 回补库存，支付/取消幂等跳过 |
| 3 | Seata AT 分布式事务 | `@GlobalTransactional` 收敛下单链路（order + stock 双分支），库存不足全局回滚零残留；自补 Feign XID 传播 |
| 4 | Redis Lua 库存预扣 | 库存闸门前置到 Redis（三态 Lua 脚本），DB 乐观锁兜底；全降级设计（Redis 为加速层非依赖层），双侧对账 |
| 5 | Sentinel 限流熔断 | 网关 API 分组限流（429 统一 JSON）+ order 服务熔断降级（fallbackFactory） |
| 6 | 管理后台 | 独立 admin 账号 + JWT role=admin 网关鉴权（401/403 分级）；看板聚合 / 商品 / 分类 / 库存（Redis 双侧核对）/ 订单（发货闭环）/ 用户六模块 |
| 7 | ES 中文搜索 | 商品搜索接入 Elasticsearch + IK 分词（multi_match 加权、相关度/销量/价格排序）；**变更实时同步 + 启动全量重建 + ES 故障自动降级 DB**，接口契约不变 |

## 模块结构

| 模块 | 端口 | 职责 |
|---|---|---|
| eshop-common | — | 公共库：统一响应 Result / PageResult、业务异常、全局异常处理、JWT 工具、跨服务 DTO、订单状态机常量 |
| eshop-gateway | 9000 | 统一入口：路由转发 / JWT 鉴权（用户/管理员双策略）/ 内部路径拦截（/internal/** → 404）/ Sentinel 限流 |
| eshop-user | 8081 | 注册登录（JWT 签发）、用户资料、收货地址、管理员认证、管理端用户管理 |
| eshop-product | 8082 | 分类、商品列表、**ES 搜索（IK 分词 + 降级）**、商品详情（缓存三件套）、管理端商品/分类、内部 SKU 批量接口 |
| eshop-stock | 8083 | 库存查询、Redis Lua 预扣 + DB 乐观锁、内部 锁定/确认/回补、管理端库存（双侧对账/调整/预热） |
| eshop-cart | 8084 | 购物车（Redis Hash + OpenFeign 补充商品信息） |
| eshop-order | 8085 | 下单（Seata + 快照 + 锁库存）、订单状态机、RocketMQ 延迟关单、管理端订单（发货）/数据看板 |
| eshop-pay | 8086 | 支付单（1:1 订单）、模拟回调（幂等三重防护） |
| eshop-web | 5173 | 商城 C 端前端（Vue 3 + Vite + Pinia + Axios；开发态经 Vite 代理 /api → 网关） |
| eshop-admin | 5174 | 管理后台前端（Vue 3 + Element Plus + ECharts；按原型 1:1 实现，对接 /api/admin/**） |

路由规范：`/api/{service}/**` → `lb://eshop-{service}`；`/api/admin/{域}/**` → 各服务 `/admin/{域}/**`（网关校验 role=admin）。
鉴权约定：`Authorization: Bearer <token>`；网关校验后以 `X-User-Id` 透传身份（详见 [接口契约](docs/api-contract.md) §1.4）。
内部接口：统一 `/internal/` 前缀，网关一律 404；服务间经 Nacos 直连调用。

## 快速开始

前置依赖（详见 [环境手册](docs/environment-setup.md)）：JDK 21、Node 18+、MySQL 8、Redis、Nacos 3.0.3、RocketMQ 5（order 服务延迟关单用）、Seata Server 2.5（下单分布式事务用）、Elasticsearch 8.18 + IK（搜索用）。

```bash
# ========== 1. 数据库 ==========
mysql -uroot -p --default-character-set=utf8mb4 < sql/00-create-databases.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/01-shop_user.sql    # 依次执行 01 ~ 07
mysql -uroot -p --default-character-set=utf8mb4 < sql/06-seed-data.sql    # 演示数据
mysql -uroot -p --default-character-set=utf8mb4 < sql/08-admin-seed.sql   # 管理员账号 admin/admin123

# ========== 2. 中间件（启动顺序重要） ==========
# Nacos 3.0.3 → Redis → RocketMQ(namesrv + broker) → Seata Server → Elasticsearch
# 说明：RocketMQ / Seata / ES 未启动时核心链路仍可用（MQ 发送失败不阻断下单、
#       Seata 仅在 order+stock 参与全局事务时必需、搜索自动降级 DB）

# ========== 3. 构建与启动服务 ==========
mvn -DskipTests package
java -jar eshop-gateway/target/eshop-gateway-1.0.0-SNAPSHOT.jar --server.port=9000
java -jar eshop-user/target/eshop-user-1.0.0-SNAPSHOT.jar    --server.port=8081
java -jar eshop-product/target/eshop-product-1.0.0-SNAPSHOT.jar --server.port=8082
java -jar eshop-stock/target/eshop-stock-1.0.0-SNAPSHOT.jar  --server.port=8083
java -jar eshop-cart/target/eshop-cart-1.0.0-SNAPSHOT.jar    --server.port=8084
java -jar eshop-order/target/eshop-order-1.0.0-SNAPSHOT.jar  --server.port=8085   # 需先启动 RocketMQ + Seata
java -jar eshop-pay/target/eshop-pay-1.0.0-SNAPSHOT.jar      --server.port=8086

# ========== 4. 前端 ==========
cd eshop-web   && npm install && npm run dev   # C 端   http://127.0.0.1:5173（演示账号 sinon / 123456）
cd eshop-admin && npm install && npm run dev   # 管理端 http://127.0.0.1:5174（admin / admin123）

# ========== 5. 验证 ==========
# Nacos 控制台 http://127.0.0.1:8080（服务列表应有 7 个 eshop-* 服务）
# 网关路由     curl http://127.0.0.1:9000/api/user/health
# 接口文档     各服务 /doc.html（如 http://127.0.0.1:8081/doc.html）
```

### 一键冒烟脚本（scripts/）

| 脚本 | 覆盖 |
|---|---|
| smoke-test.ps1 | 基础四域：认证 / 商品 / 库存 / 购物车 |
| smoke-test-order.ps1 | 订单/支付全链路（下单 → 支付 → 取消回补，DB 级核对） |
| smoke-test-cache.ps1 | 缓存三件套：命中率 / 空值 / 互斥锁 / 延迟双删 |
| smoke-test-mq.ps1 | RocketMQ 延迟关单（10s 测试档）+ 回补幂等 |
| smoke-test-stock-lua.ps1 | Redis Lua 预扣三态 + 双侧核对 |
| smoke-test-admin.ps1 | 管理后台：鉴权策略（401/403）/ 六域接口 / 发货闭环 |
| smoke-test-es.ps1 | ES 搜索：IK 分词 / reindex / 加权 / 排序 / 过滤 / 上下架与新增实时同步 |
| admin-ui-smoke.mjs、admin-e2e.mjs | 管理后台 UI 冒烟 + 端到端交互（playwright-core 驱动 Edge） |

## 文档索引

| 文档 | 内容 |
|---|---|
| [docs/development-plan.md](docs/development-plan.md) | 开发规划：定位、架构、路线图（P0-P4）、技术亮点清单 |
| [docs/version-matrix.md](docs/version-matrix.md) | 版本矩阵：全部依赖锁定版本与选型依据 |
| [docs/er-design.md](docs/er-design.md) | 数据库设计：五库 ER 模型与关键设计决策 |
| [docs/environment-setup.md](docs/environment-setup.md) | 环境手册：中间件安装、启动方式、踩坑记录 |
| [docs/highlights.md](docs/highlights.md) | 技术亮点详解：设计动机、实现要点、验证方式 |

## 开发路线

| 阶段 | 状态 |
|---|---|
| P0 骨架（版本/ER/工程/连通性） | ✅ 2026-10-14 |
| P1 核心链路（浏览→支付全流程 + C 端前端） | ✅ 2026-10-14（tag `p1`） |
| P2 技术亮点（缓存/MQ关单/Seata/Lua预扣/Sentinel/压测） | ✅ 2026-10-14（tag `p2`） |
| P3 管理后台（原型 → 后端 admin 体系 → eshop-admin 前端） | ✅ 2026-10-14 |
| P4 打磨上线（ES 搜索 ✅ / Docker Compose ⏳ / 内网穿透 ⏳） | 进行中 |
