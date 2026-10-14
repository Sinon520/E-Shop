# E-Shop 环境准备手册

> 状态：持续更新（P0 阶段）｜ 最后更新：2026-10-14
> 记录本机中间件的安装位置、启动方式、验证结果与踩坑记录

---

## 1. Nacos（注册中心 / 配置中心）✅ 已就绪

| 项 | 值 |
|---|---|
| 版本 | **3.0.3**（2026-10-14 从 2.3.2 升级，对齐 SCA 2025.0.0.0） |
| 安装目录 | `D:\springcloudalibabatool\nacos-3.0.3\nacos` |
| 旧版本目录 | `D:\springcloudalibabatool\nacos-server-2.3.2`（保留备回退，当前未运行） |
| 客户端端口 | **8848**（HTTP / 客户端）；**9848**（gRPC，=8848+1000，自动） |
| 控制台 | **http://127.0.0.1:8080**（本地开发已关闭控制台登录，直接访问） |
| 持久化 | MySQL 库 **`nacos3`**（root/123456；3.x 全新表结构，**勿复用**旧库 `nacos2.3.2`） |
| 运行状态 | ✅ 已验证：8848/9848/8080 三端口监听，控制台 HTTP 200 |

### 启动方式

**方式 A（日常使用）：官方脚本**
```cmd
cd /d D:\springcloudalibabatool\nacos-3.0.3\nacos\bin
startup.cmd -m standalone
```
鉴权参数已预置 → 无交互提示；关闭该窗口即停止服务。

**方式 B（WorkBuddy 环境内 / 自动化）：专用脚本**
```powershell
powershell -ExecutionPolicy Bypass -File D:\springcloudalibabatool\start-nacos-3.0.3.ps1
```
（脚本内置环境变量清理，原因见下方坑 1）

### 停止方式
- 关闭启动窗口，或运行 `bin\shutdown.cmd`；兜底：结束对应 java 进程。

### ⚠️ 坑 1：运行环境会注入 `SERVER__PORT`（双下划线！）劫持端口

**现象**：Nacos 启动报 `Web server failed to start. Port 52502 was already in use`；且 `-Dnacos.server.main.port=8848`、清理 `SERVER_PORT`（单下划线）均无效。

**根因**：WorkBuddy 运行时给子进程注入 `SERVER__PORT=52502` 与 `SERVER__HOST=127.0.0.1`（52502 被 WorkBuddy.exe 自身占用）。Spring Boot 松散绑定把 `SERVER__PORT` 解释为 `server.port`，且**环境变量优先级高于配置文件**，于是 Nacos 的 server web 上下文被拽到 52502。

**对策**：
- 启动前执行 `Remove-Item Env:SERVER__PORT`（双下划线，注意不是 `SERVER_PORT`）
- 普通 Spring Boot 微服务：命令行 `--server.port=xxxx` 即可覆盖（命令行参数优先级最高）
- 多端口应用（如 Nacos）不能用 `--server.port`，必须用「先清变量」方案

### ⚠️ 坑 2：Nacos 3.x 与 2.x 的配置差异（升级时踩过）

- **MySQL 数据源键名变化**：`spring.sql.init.platform=mysql` + `db.num=1` + `db.url.0` + **`db.user` / `db.password`**（3.x 不再带 `.0` 后缀）
- **控制台独立端口**：`nacos.console.port=8080`（2.x 时代控制台挂在 8848）
- **控制台默认开鉴权**：本地开发在 `conf/application.properties` 设 `nacos.core.auth.console.enabled=false`
- **首次启动交互引导**：若 `token.secret.key` / `server.identity.key` / `server.identity.value` 为空会进入交互提问（自动化环境会卡死）→ 必须预先填好

---

## 2. MySQL ✅ 已就绪

- 8.0.37，Windows 服务 `MySQL80`（开机自启）；root / 123456
- 业务库：`shop_user` / `shop_product` / `shop_stock` / `shop_order` / `shop_pay`（DDL 已备好于 `sql/`，待执行）
- 中间件库：`nacos3`

## 3. Redis ✅ 已验证

- 目录 `D:\Redis-8.10.0-Windows-x64-cygwin-with-Service\...`；双击 `start.bat` 启动
  （PS 命令行方式需传 cygwin 路径：`redis-server.exe /cygdrive/d/.../redis.conf`）
- 端口 6379，无密码；**未注册服务，使用前需手动启动**
- **持久化实测**（2026-10-14）：
  - `CONFIG GET save` = `3600 1 300 100 60 10000` —— RDB 默认档生效（conf 中 save 行未启用时，Redis 使用内置默认快照档）
  - `appendonly` = `no` —— AOF 未启用
  - 含义：进程/电脑重启 → 从 `dump.rdb` 恢复，最多丢"最后一次快照之后的写入"（低频写入场景下窗口最大约 1 小时）
  - 可选优化（希望"重启几乎零丢失"时）：① conf 里把快照调密（如 `save 60 1`）；② 或启用 AOF（`appendonly yes` + `appendfsync everysec`，最多丢 1 秒）

## 4. Seata Server（待联通验证）

- 目录 `D:\springcloudalibabatool\seata-server-2.5.0`；服务端端口 8091
- 客户端版本将与 server 对齐 **2.5.0**（与 SCA 2025.0.0.0 矩阵一致）

## 5. Sentinel Dashboard（待联通验证）

- `D:\springcloudalibabatool\sentinel-dashboard-1.8.10.jar`
- 建议启动端口 **8858**（默认 8080 与 Nacos 控制台冲突，启动时显式指定）

## 6. RocketMQ（待联通验证）

- 目录 `D:\rocketmq-all-5.5.0-bin-release`；NameServer 9876 + Broker 10911
- 环境变量 `ROCKETMQ_HOME` 已配置

---

## 附：全项目端口规划

| 端口 | 用途 |
|---|---|
| 8848 / 9848 | Nacos 客户端 / gRPC |
| 8080 | Nacos 控制台 |
| 3306 | MySQL |
| 6379 | Redis |
| 9876 / 10911 | RocketMQ NameServer / Broker |
| 8091 | Seata Server |
| 8858 | Sentinel Dashboard |
| 9000 | eshop-gateway |
| 8081 ~ 8086 | user / product / stock / cart / order / pay |
| 5173 / 5174 | 前端 dev：商城 C 端 / 管理后台 |
