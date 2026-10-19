# Seata AT 实现笔记（E-Shop 学习向 · 简洁版）

> 目标：说清「Seata 是什么、本项目怎么接入的、被追问时怎么答」。
> 本项目用 Seata 只解决一个问题：**下单 = 订单落库（shop_order）+ 锁库存（shop_stock），跨两个库必须同生共死**。

---

## 一、AT 模式两句话原理

1. **一阶段**：每个参与方（RM）执行本地事务时，Seata 自动把**数据前后镜像**记到本库的 `undo_log` 表，然后**直接提交**（不长期锁行）。
2. **二阶段**：全局事务成功 → 异步删 undo_log（几乎零成本）；任一分支失败 → TC 通知各分支**按 undo_log 反向补偿**（自动生成回滚 SQL），数据回到事务前状态。

对比传统 XA：XA 两阶段全程持锁、吞吐低；AT 一阶段就提交、靠 undo_log 补偿，**性能好但保证的是最终一致**（回滚有短暂窗口）。

---

## 二、本项目接入：就四件事

### ① 依赖（order 和 stock 都要）

```xml
<dependency>
    <groupId>org.apache.seata</groupId>
    <artifactId>seata-spring-boot-starter</artifactId>  <!-- 2.5.0，父 pom 管版本 -->
</dependency>
```

### ② 每个参与方的库里建 undo_log 表（`sql/07-undo-log.sql`）

`shop_order` 和 `shop_stock` 各一张，关键列：`xid`（全局事务 id）+ `branch_id` + `rollback_info`（前后镜像，LONGBLOB），唯一键 `(xid, branch_id)`。

### ③ 配置（order 和 stock 的 application.yml 结构相同）

```yaml
seata:
  enabled: true
  tx-service-group: eshop_tx_group          # 事务分组名（逻辑名，自定义）
  service:
    vgroup-mapping:
      eshop_tx_group: default               # 分组 → TC 集群名（seata-server 的 cluster=default）
  registry:
    type: nacos                             # 从 Nacos 发现 TC（seata-server）
    nacos:
      application: seata-server
      server-addr: 127.0.0.1:8848
      group: SEATA_GROUP
      username: ${NACOS_USERNAME}           # 真实值在 application-local.yml
      password: ${NACOS_PASSWORD}
```

**概念对齐**：`tx-service-group` 是应用侧的逻辑分组，`vgroup-mapping` 把它映射到 TC 的集群名，`registry` 负责"去哪找 TC"——三者串起来才能连上 seata-server。

### ④ 发起方打一个注解（`OrderService.create`，全文唯一的全局事务边界）

```java
@GlobalTransactional(timeoutMills = 60000, name = "create-order")
public OrderCreateVO create(Long userId, OrderCreateRequest request) {
    // 1. 校验/合并商品项（本地：shop_order 落订单、明细、状态日志）
    // 2. Feign 调 stock 锁库存（stock 库写 t_stock + 流水）
    // 3. 发 RocketMQ 延迟关单消息
    //    ↑ 任一步失败/异常 → 全局回滚：订单分支被 undo_log 撤销，库里不留半成品订单
}
```

stock 服务**不需要任何注解**——seata-spring-boot-starter 会自动代理它的数据源（DataSourceProxy），SQL 执行时自动记录 undo_log；只要请求头里带 XID，它就自动加入全局事务成为分支。

---

## 三、关键补丁：Feign 的 XID 传播（`SeataFeignConfig`）

**问题**：`seata-spring-boot-starter` 自带 HTTP **服务端**集成（下游能从请求头恢复 XID），但**没有内置 Feign 客户端集成**——order 发起的 Feign 调用不会带 XID，stock 收到请求以为"不在全局事务里"，就不注册分支、不记 undo_log，回滚时库存回不来。

**解法**（一个 Feign 拦截器，这是本项目 Seata 最有含金量的 10 行）：

```java
@Bean
public RequestInterceptor seataXidRequestInterceptor() {
    return template -> {
        String xid = RootContext.getXID();          // 当前线程的全局事务 ID
        if (xid != null && !xid.isEmpty()) {
            template.header(RootContext.KEY_XID, xid);   // 写入请求头 TX_XID
        }
    };
}
```

链路：`@GlobalTransactional` 开启 → TC 生成 XID → **XID 随 Feign 请求头传到 stock** → stock 的 HTTP 集成从请求头恢复 XID → 注册分支、记 undo_log → 失败时 TC 通知它一起回滚。

---

## 四、全链路（以"锁库存失败"为例）

```
order.create 开启全局事务（TC 生成 XID）
  ├─ 分支1：shop_order 落订单（本地事务 + undo_log）→ 立即提交
  ├─ Feign 调 stock（请求头带 XID）
  │    └─ 分支2：stock 库 UPDATE 锁库存 → 乐观锁重试 3 次全失败 → 抛 BizException(3002)
  └─ 异常冒泡到 @GlobalTransactional → TM 通知 TC 全局回滚
       ├─ 分支2：无已提交变更（乐观锁没成功），无需补偿
       └─ 分支1：按 undo_log 前镜像生成反向 SQL → 订单记录被撤销
结果：两个库零残留，用户看到"库存不足"，不会出现"库存没扣到但订单生成了"。
```

验证：`smoke-test-order.ps1` 库存不足场景 + DB 级核对（无半成品订单）；压测 1215 并发零超卖同样覆盖该链路。

---

## 五、面试问答（4 问）

**Q1：AT 模式和 XA 的区别？**
A：XA 两阶段全程持锁、吞吐低但强一致；AT 一阶段本地提交（undo_log 记前后镜像），二阶段成功删日志 / 失败按镜像反向补偿，性能好，保证最终一致。

**Q2：undo_log 是什么、谁写的？**
A：参与方库里的一张表。RM 执行 SQL 时被 Seata 代理数据源拦截，自动记录前镜像/后镜像；全局回滚时按前镜像生成反向 SQL 补偿，成功后自动清理。

**Q3：XID 怎么跨服务传播的？**
A：本项目 Feign 场景 starter 没有内置客户端集成，自写了一个 RequestInterceptor：从 `RootContext.getXID()` 取当前 XID 写入请求头 `TX_XID`，下游 starter 的 HTTP 集成从请求头恢复 XID 后注册分支。

**Q4：全局回滚时业务已经"提交"了，怎么撤回？**
A：靠一阶段记的 undo_log 前镜像反向补偿——这就是 AT"最终一致"的含义，回滚完成前存在短暂的不一致窗口；对隔离性要求极端的场景可以上 `@GlobalLock` 或转 TCC。
