# E-Shop 电商商城

基于 Spring Cloud 微服务架构的 B2C 自营商城，覆盖「浏览 → 加购 → 下单 → 支付 → 履约」完整交易链路。

> **技术栈**：JDK 21 · Spring Boot 3.5 · Spring Cloud 2025.0 · Spring Cloud Alibaba 2025.0 · Nacos 3 · MySQL 8 · Redis · RocketMQ · Seata · Vue 3 + Vite + Pinia（C 端已实现）
>
> **当前状态**：**P1 后端 + C 端前端完成** —— 7 个服务 + 商城 C 端（eshop-web）就绪，「浏览 → 加购 → 下单 → 支付成功」全链路可演示（开发态前端经 Vite 代理直连网关；P2 技术亮点待启动）

## 模块结构

| 模块 | 端口 | 职责 |
|---|---|---|
| eshop-common | — | 公共库：统一响应 Result / PageResult、业务异常、全局异常处理、JWT 工具、跨服务 DTO、订单状态机常量 |
| eshop-gateway | 9000 | 统一入口：路由转发 / JWT 鉴权 / 内部路径拦截（/internal/** → 404）/ 跨域（限流在 P2 接入） |
| eshop-user | 8081 | 注册登录（JWT 签发）、用户资料、收货地址 |
| eshop-product | 8082 | 分类、商品列表（筛选/排序/分页）、商品详情、内部 SKU 批量接口 |
| eshop-stock | 8083 | 库存查询 + 内部 锁定/确认/回补（乐观锁 + 流水幂等） |
| eshop-cart | 8084 | 购物车（Redis Hash + OpenFeign 补充商品信息） |
| eshop-order | 8085 | 下单（快照 + 锁库存）、订单状态机、列表详情、取消回补 |
| eshop-pay | 8086 | 支付单（1:1 订单）、模拟回调（幂等三重防护） |
| eshop-web | 5173 | 商城 C 端前端（Vue 3 + Vite + Pinia + Axios；开发态经 Vite 代理 /api → 网关） |

路由规范：`/api/{service}/**` → `lb://eshop-{service}`（网关自动去前缀转发）。
鉴权约定：登录后携带 `Authorization: Bearer <token>`；网关校验通过后以 `X-User-Id` 透传用户身份（详见 [接口契约](docs/api-contract.md) §1.4）。
内部接口：统一 `/internal/` 前缀，网关一律 404；服务间经 Nacos 直连调用。

## 快速开始

前置依赖（详见 [环境手册](docs/environment-setup.md)）：MySQL 8、Nacos 3.0.3、Redis。

```bash
# 1. 初始化数据库（5 库 13 表 + 开发种子数据）
mysql -uroot -p --default-character-set=utf8mb4 < sql/00-create-databases.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/01-shop_user.sql
# ... 依次执行 02 ~ 05
mysql -uroot -p --default-character-set=utf8mb4 < sql/06-seed-data.sql   # 可选：演示数据

# 2. 构建（BUILD 前确保 Nacos 已启动）
mvn -DskipTests package

# 3. 启动服务（每个模块一个进程，端口见上表）
java -jar eshop-gateway/target/eshop-gateway-1.0.0-SNAPSHOT.jar --server.port=9000
java -jar eshop-user/target/eshop-user-1.0.0-SNAPSHOT.jar --server.port=8081
# ... 其余服务同理（product 8082 / stock 8083 / cart 8084 / order 8085 / pay 8086）

# 4. 验证
#    Nacos 控制台：http://127.0.0.1:8080 （服务列表应有 7 个 eshop-* 服务）
#    网关路由：curl http://127.0.0.1:9000/api/user/health
#    接口文档：各服务 /doc.html（如 http://127.0.0.1:8081/doc.html ）
#    一键冒烟：powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1         # 基础四域
#              powershell -ExecutionPolicy Bypass -File scripts/smoke-test-order.ps1  # 订单/支付全链路（需先跑基础脚本建账号）

# 5. 启动前端 C 端（Node 18+；开发态经 Vite 代理 /api → 网关 9000）
cd eshop-web
npm install
npm run dev    # http://127.0.0.1:5173 （演示账号 sinon / 123456）
```

## 文档索引

| 文档 | 内容 |
|---|---|
| [docs/development-plan.md](docs/development-plan.md) | 开发规划：定位、架构、路线图（P0-P4）、技术亮点清单 |
| [docs/version-matrix.md](docs/version-matrix.md) | 版本矩阵：全部依赖锁定版本与选型依据 |
| [docs/er-design.md](docs/er-design.md) | 数据库设计：五库 ER 模型与关键设计决策 |
| [docs/environment-setup.md](docs/environment-setup.md) | 环境手册：中间件安装、启动方式、踩坑记录 |

## 开发路线

| 阶段 | 状态 |
|---|---|
| P0 骨架（版本/ER/工程/连通性） | ✅ 已完成（2026-10-14） |
| P1 核心链路（浏览→支付全流程） | ⏳ 进行中 |
| P2 技术亮点（缓存/MQ/Seata/压测） | 待开始 |
| P3 管理后台 | 待开始 |
| P4 打磨上线（ES/Docker/演示） | 待开始 |
