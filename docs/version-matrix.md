# E-Shop 版本矩阵（Phase 0 · 已锁定）

> 锁定日期：2026-10-14 ｜ 依据：SCA 官方版本说明 + Spring 官方支持周期 + 本机环境实证
> **结论**：Spring Boot **3.5.16** + Spring Cloud **2025.0.2** + Spring Cloud Alibaba **2025.0.0.0**
> **唯一环境变更**：Nacos Server 从 2.3.2 升级到 3.0.3（原因见第三节）

---

## 一、锁定矩阵

### 1.1 核心框架

| 组件 | 锁定版本 | 本机状态 | 说明 |
|---|---|---|---|
| JDK | 21（LTS） | ✅ 已装 | Boot 3.5 支持 17–25 |
| Maven | 3.9.9 | ✅ 已装 | 阿里云镜像 |
| Spring Boot | **3.5.16** | 待引入 | 3.x 终版（最终 OSS 补丁 2026-06-25） |
| Spring Cloud | **2025.0.2** | 待引入 | 2025.0.x 线最新补丁（2026-05） |
| Spring Cloud Alibaba | **2025.0.0.0** | 待引入 | 官方适配：Boot 3.5.x + Cloud 2025.0.x |

### 1.2 微服务组件（SCA 2025.0.0.0 官方配套）

| 组件 | 锁定版本 | 本机状态 | 说明 |
|---|---|---|---|
| Nacos | **3.0.3**（client + server） | ⚠️ server 需升级（现 2.3.2） | client 随 SCA 自带；server 需下载 3.0.3 |
| Sentinel | 1.8.9（client）＋ 1.8.10（Dashboard） | ✅ Dashboard 已装 | Dashboard ≥ client，兼容 |
| Seata | **2.5.0**（client + server） | ✅ server 已装 | 与官方矩阵完全一致，零改动 |
| RocketMQ | server 5.5.0（已装）＋ starter **2.3.6** | ✅ server 已装 | starter 内置 client 5.x，与 5.5.0 同线兼容 |

### 1.3 数据层与工具库

| 组件 | 锁定版本 | 本机状态 | 说明 |
|---|---|---|---|
| MySQL | 8.0.37 | ✅ 已装 | |
| Connector/J | 随 Boot BOM | — | 不手动指定 |
| MyBatis-Plus | **3.5.17** | 待引入 | 需同时引入 mybatis-plus-jsqlparser（3.5.9 起解耦） |
| Redis | 8.10.0 | ✅ 已装 | |
| Redisson | **3.52.0** | 待引入 | Boot 3.5 线最后适配版（内置 redisson-spring-data-35） |
| JJWT | **0.12.6** | 待引入 | |
| Knife4j | **4.6.0** | 待引入 | groupId 已变更为 `com.github.xingfudeshi`（社区延续维护） |
| Lombok | 随 Boot BOM | 待引入 | |
| JMeter | 5.x | 待安装 | Phase 2 压测用 |

### 1.4 前端（另行锁定时）

Vue 3 + Vite 5 + Pinia + Element Plus —— 具体小版本在搭前端时用 `npm create vue` 生成后记录。

---

## 二、版本线为什么这么选

### 2.1 为什么 Boot 3.5 而不是 4.x

2026-10 时点，Boot 3.x 全线已过 OSS 支持期，唯一活跃线是 Boot 4.0/4.1。但本作品集选 3.5.16：

1. **生态成熟度**：SCA 对 Boot 4 的适配（2025.1.0.0，2026-02 发布）仅 8 个月，中文教程几乎为零；面试官自身多为 3.x 经验，讲 4.x 反而有被问冷场的风险
2. **组件对齐**：SCA 2025.0.0.0 官方对应的 **Seata 正好是 2.5.0**——与本机已装服务端完全一致，零成本
3. **企业现实**：3.5 是 3.x 终版，企业存量系统与新项目主力；"选成熟稳定线"本身就是可被认可的工程判断
4. 后续升级路径清晰：将来可平滑迁到 4.x（SCA 2025.1 线）

### 2.2 为什么不用 SCA 2023 线（Boot 3.3）

- Boot 3.3 已 EOL 16 个月（2025-06 终止）
- 该线 Seata 默认 2.2.0，与本机 server 2.5.0 不一致，需手动覆盖版本——而 2025.0.0.0 线不需要
- 结论：2023 线仅是"教程存量多"，对本项目没有决定性优势

### 2.3 为什么必须升级 Nacos Server

- SCA 2025.0.0.0 自带 **nacos-client 3.0.3**（官方矩阵硬绑定）
- Nacos 官方文档明确：**3.0 客户端默认使用 public 命名空间，在默认命名空间下连接 2.x 服务端不兼容**
- 3.x 服务端对 3.x 客户端有完整兼容保证 → server 升级是唯一稳妥解
- 升级成本低：本地 standalone，下载解压改配置（见第三节）

### 2.4 其余组件为何不动

| 组件 | 结论 | 依据 |
|---|---|---|
| Seata server 2.5.0 | 保留 | = SCA 矩阵版本，client 同版本 |
| Sentinel Dashboard 1.8.10 | 保留 | Dashboard ≥ client（1.8.9），向后兼容 |
| RocketMQ server 5.5.0 | 保留 | 5.x 大版本内 client/server 互兼容 |
| MySQL / Redis | 保留 | 与框架无耦合，版本无关 |

---

## 三、唯一环境变更：Nacos 2.3.2 → 3.0.3

> 在 P0「中间件连通性验证」环节执行

### 操作步骤

1. **停掉**现有 Nacos 2.3.2 进程
2. **下载**：`nacos-server-3.0.3.zip`
   - 官方地址：`https://github.com/alibaba/nacos/releases/download/3.0.3/nacos-server-3.0.3.zip`
   - GitHub 直连不稳时走镜像：`https://ghfast.top/https://github.com/alibaba/nacos/releases/download/3.0.3/nacos-server-3.0.3.zip`
3. **解压**到新目录（如 `D:\nacos-3.0.3`；保留 2.3.2 目录以备回退）
4. **改配置** `conf/application.properties` 追加两行（本地开发关鉴权）：
   ```properties
   nacos.core.auth.enabled=false
   nacos.core.auth.plugin.nacos.token.secret.key=U2VjcmV0S2V5MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODk=
   ```
   （secret key 为本地示例值，生产环境必须更换）
5. **启动**：`bin\startup.cmd -m standalone`（或沿用 java -jar 方式）
6. **访问**：
   - 控制台：`http://127.0.0.1:8080`（账号 nacos / nacos）——注意端口从 8848 变为 **8080**
   - 客户端连接端口仍为 **8848**，gRPC **9848**（服务配置里 `server-addr: 127.0.0.1:8848` 不变）
7. **持久化二选一**：
   - derby（默认，最简，适合本地开发）
   - MySQL（沿用习惯）：新建 `nacos3` 库 + 导入发行包自带 `conf/mysql-schema.sql`（Nacos 3 表结构与 2.x 有差异，不要复用旧库）

### 注意事项

- 控制台 8080 端口若被占用：`application.properties` 中改 `nacos.console.port=8090`
- 旧 Nacos 2.3.2 上的注册数据无需迁移（本地开发，重新注册即可）

---

## 四、版本落地方式（父 pom 骨架预览）

搭建骨架时按此结构（P0 第 3 项直接使用）：

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.16</version>
</parent>

<properties>
    <spring-cloud.version>2025.0.2</spring-cloud.version>
    <spring-cloud-alibaba.version>2025.0.0.0</spring-cloud-alibaba.version>
    <mybatis-plus.version>3.5.17</mybatis-plus.version>
    <redisson.version>3.52.0</redisson.version>
    <jjwt.version>0.12.6</jjwt.version>
    <knife4j.version>4.6.0</knife4j.version>
    <rocketmq-spring.version>2.3.6</rocketmq-spring.version>
    <seata.version>2.5.0</seata.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-dependencies</artifactId>
            <version>${spring-cloud-alibaba.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- MyBatis-Plus / Redisson / jjwt / Knife4j / rocketmq-spring / seata 单点版本在此统一管理 -->
    </dependencies>
</dependencyManagement>
```

关键纪律：
- **所有子模块只写 groupId + artifactId，不写 version**（版本唯一来源是父 pom）
- Seata / RocketMQ 的 starter 版本显式管理（与本地服务端对齐），不依赖 SCA 默认传递

---

## 五、P0 验证清单（锁版本落地标准）

- [ ] 7 个服务全部注册进 Nacos 3.0.3 控制台（8080 界面可见）
- [ ] Seata 2.5.0 全局事务跑通（服务端/客户端 tx_group 对齐）
- [ ] RocketMQ 延迟消息收发验证
- [ ] Sentinel Dashboard 能看到服务并配置流控
- [ ] 每个服务 Knife4j 文档页（/doc.html 或 swagger-ui）正常渲染
- [ ] Redisson 分布式锁最小用例通过

---

## 六、P5 AI 服务（Python）版本矩阵

> 2026-10-19 锁定。解释器：`C:\Users\asus\AppData\Local\Python\bin\python.exe`（Python **3.14.4**，pythonhome 已装全套）

| 组件 | 版本 | 说明 |
|---|---|---|
| Python | 3.14.4 | 本机 pythonhome |
| FastAPI | 0.146.0 | 接入层 |
| Starlette | 1.9.0 | FastAPI 依赖 |
| Uvicorn | 0.52.4 | ASGI 服务器 |
| LangChain | 1.3.16 | **1.x 线，API 与 0.3 差异大** |
| langchain-core | 1.6.0 | |
| langchain-openai | 1.6.0 | 走 OpenAI 兼容协议接智谱 / DeepSeek / Ollama |
| LangGraph | 1.2.11 | 状态机编排 |
| langgraph-checkpoint | 4.2.0 | |
| langgraph-checkpoint-redis | 0.5.2 | `RedisSaver` 来源 |
| langgraph-prebuilt | 1.1.0 | `ToolNode` 来源 |
| Pydantic | 2.13.4 | |
| pydantic-settings | 2.15.0 | 配置读取 |
| HTTPX | 0.28.1 | 直连 Java 服务的客户端 |
| redis-py | 8.1.0 | |
| elasticsearch-py | 8.19.3 | 对齐 ES 8.18 服务端 |

关键纪律：

- **本机不新建 venv**：直接使用 pythonhome 的 3.14.4（已装全套）；仓库提供 `requirements.txt`，他人 clone 后自建环境即可复现
- **锁定 LangGraph 版本**：1.x 迭代快，图 API 变动会在导入期直接报错
- **LangChain 1.x 与网上多数 0.3 教程写法不同**：`create_react_agent` 已移至 `langchain_classic`，新入口是 `langchain.agents.create_agent`——本项目走显式建图，不受影响
- **pip 源现状**：两个配置文件共存——`C:\Users\asus\pip\pip.ini`（清华，2024 年旧文件）与 `C:\Users\asus\AppData\Roaming\pip\pip.ini`（阿里云，标准用户级路径）。**实际生效的是阿里云**（`pip config get global.index-url` 已验证）；若某次解析异常，可显式加 `-i https://mirrors.aliyun.com/pypi/simple/ --trusted-host mirrors.aliyun.com` 覆盖

---

## 附：信息来源（2026-10-14 查询）

- SCA 官方版本说明（2025.x）：https://sca.aliyun.com/docs/2025.x/overview/version-explain
- SCA 官方版本说明（2023.x）：https://sca.aliyun.com/docs/2023/overview/version-explain/
- Spring Boot 支持周期：https://endoflife.date/spring-boot ｜ https://spring.io/blog/2025/06/19/spring-boot-3-3-13-available-now/
- Nacos 3.x 客户端兼容性与升级手册：https://nacos.io/docs/next/manual/admin/upgrading
- Nacos Java SDK（3.0 命名空间变更说明）：https://nacos-group.github.io/docs/latest/manual/user/java-sdk/usage/
- springdoc 兼容矩阵：https://springdoc.org/v2/faq.html
- RocketMQ Spring 版本历史：https://repo.maven.apache.org/maven2/org/apache/rocketmq/rocketmq-spring-boot-starter/
- Redisson 与 Spring Boot 兼容矩阵：https://redisson.pro/blog/redisson-spring-boot-4-compatibility.html
- MyBatis-Plus 安装说明：https://baomidou.com/en/getting-started/install
