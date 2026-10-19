# Redis 与 RocketMQ 实现笔记（E-Shop · 代码提取 + 讲解版）

> 定位：把项目里 Redis / RocketMQ 的**全部关键代码集中提取**，逐段讲「怎么写」与「为什么这么写」。
> 全部代码摘自仓库真实实现，文件路径就地标注；读完应能对着任意一段代码讲清设计动机，面试被追问时不卡壳。
> 阅读顺序：先看 〇 代码地图建立全局，再按 一（Redis 四处）→ 二（RocketMQ 一处）→ 三（问答速查）推进。

---

## 〇、代码地图（先看这张表）

| 模块 | 文件 | 职责 | 关键技术点 |
|---|---|---|---|
| product | `cache/ProductCacheService.java` | 商品缓存读写主逻辑 | Cache Aside、空值标记、布隆、互斥锁、TTL 抖动 |
| product | `cache/CacheEvictWorker.java` | 延迟双删的"第二次删除" | ZSET 自研延迟队列、SmartLifecycle |
| product | `cache/CacheKeys.java` | 缓存 key 常量 | 统一 `product:` 前缀 |
| product | `config/RedissonConfig.java` | Redisson 客户端 | 手动装配、JsonJacksonCodec |
| product | `config/ProductCacheProperties.java` | 缓存参数 | `eshop.cache.*` 配置绑定 |
| product | `runner/CachePreheatRunner.java` | 启动预热 | 异步 + 失败不阻断 |
| stock | `cache/StockRedisGate.java` | 库存 Redis 预扣闸门 | Lua 三态、全降级 |
| stock | `resources/lua/stock_deduct.lua` | 原子预扣脚本 | GET+判断+DECRBY 原子化 |
| stock | `resources/lua/stock_restore.lua` | 加回脚本 | 判 EXISTS 防凭空造余量 |
| stock | `config/StockRedisConfig.java` | Lua 脚本 Bean 注册 | DefaultRedisScript |
| stock | `service/StockService.java` | 双层防超卖整合 | 预扣补偿、乐观锁 CAS、幂等流水 |
| stock | `runner/StockPreheatRunner.java` | 启动预热/对账 | 以 DB 为准重建 |
| cart | `service/CartService.java` | Redis 购物车 | Hash 结构、滑动过期、Feign 补全 |
| cart | `model/CartItemState.java` | 购物车条目状态 | JSON value 结构 |
| order | `mq/OrderTimeoutProducer.java` | 超时消息生产者 | syncSend + 延迟级别 |
| order | `mq/OrderTimeoutConsumer.java` | 超时消息消费者 | @RocketMQMessageListener、自动重试 |
| order | `mq/OrderMqConstants.java` | 主题/消费组常量 | — |
| order | `service/OrderService.java` | 下单发消息 + 关单消费 | 失败不阻断、幂等三连 |

依赖版本：Redisson **3.52.0**、rocketmq-spring-boot-starter **2.3.6**（父 pom `dependencyManagement` 统一管理）。

---

## 一、Redis 在项目中的四处用法

| # | 位置 | 用法 | 核心类 |
|---|---|---|---|
| 1 | product | 商品/分类缓存三件套 + 延迟双删 | `ProductCacheService`、`CacheEvictWorker` |
| 2 | stock | 库存 Lua 预扣闸门（防超卖第一层） | `StockRedisGate` + `lua/stock_deduct.lua` |
| 3 | cart | 购物车（Hash 结构） | `CartService` |
| 4 | product | 分布式锁（互斥锁重建） | Redisson `RLock` |

---

## 1.1 商品缓存三件套（product）

### 先把三个词说清楚

| 词 | 含义 | 本项目的解法 |
|---|---|---|
| **穿透** | 查询**根本不存在**的数据，缓存永远不命中，请求全打到 DB | 布隆过滤器（第一层）+ 空值缓存（第二层） |
| **击穿** | 某个**热点 key 过期瞬间**，大量并发同时重建 | Redisson 互斥锁 + 双检，只放一个线程查库 |
| **雪崩** | **大批 key 同时过期**，DB 瞬间被击穿 | TTL = 基础值 + 随机抖动，过期时间错开 |

### 1.1.1 读路径：`getSpuDetail`（完整代码）

> 文件：`eshop-product/.../cache/ProductCacheService.java`

```java
public SpuDetailVO getSpuDetail(Long spuId) {
    RBucket<String> bucket = redissonClient.getBucket(CacheKeys.SPU_DETAIL_PREFIX + spuId);

    // ① 读缓存：命中返回 / 空值标记抛 2001 / 损坏自愈（删脏 key 降级查库）
    SpuDetailVO hit = readSpuDetailCache(bucket, spuId);
    if (hit != null) {
        return hit;
    }

    // ② 布隆过滤器拦截（防穿透第一层）
    if (!mightExist(spuId)) {
        log.debug("[缓存] 布隆拦截，直接判定不存在：spuId={}", spuId);
        throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
    }

    // ③ 互斥锁重建（防击穿）
    RLock lock = redissonClient.getLock(CacheKeys.SPU_LOCK_PREFIX + spuId);
    boolean locked = tryLock(lock);
    if (!locked) {
        // 竞争失败降级：直接查库返回、不写缓存，避免锁外惊群式写放大
        log.debug("[缓存] 锁竞争失败，降级直查 DB：spuId={}", spuId);
        return requireFound(productService.loadSpuDetail(spuId), spuId);
    }
    try {
        // 双检：持锁期间可能已有其他线程完成重建
        hit = readSpuDetailCache(bucket, spuId);
        if (hit != null) {
            return hit;
        }
        SpuDetailVO vo = productService.loadSpuDetail(spuId);
        writeSpuDetailCache(spuId, vo);
        return requireFound(vo, spuId);
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

**怎么写的**：一次请求按 ①缓存 → ②布隆 → ③加锁 → ④双检 → ⑤查库写缓存 顺序推进，任何一步能返回就短路。

**为什么这么写（追问高发区）**：

- **布隆过滤器为什么和空值缓存共存（两层）**：布隆说"不存在"就 100% 不存在，适合**最前置拦截**恶意扫描；但它有 1% 误判率，说"存在"不一定真存在——误判漏下来的由**空值缓存**接住（DB 查不到写 `__NULL__` 标记）。两层各管一段。
- **布隆未预热不拦截**：`mightExist` 里 `bloom.count() == 0` 直接放行——Redis 重启后布隆是空的，照常拦截会**误杀全部请求**，可用性优先。
- **锁竞争失败为什么直查 DB 而不原地等待**：等待会让 N 个线程排队串行；直查 DB 让它们各拿各的数据返回，只是不写缓存——避免 N 份重复写（写放大）。
- **双检（Double Check）为什么必要**：A 线程持锁重建完成后放锁，B 线程刚拿到锁——若不重读缓存，B 会再查一次 DB 再写一次，纯浪费。

### 1.1.2 三态读取 + 损坏自愈：`readSpuDetailCache`

```java
private SpuDetailVO readSpuDetailCache(RBucket<String> bucket, Long spuId) {
    String cached = bucket.get();
    if (cached == null) {
        return null;                            // 状态一：未命中 → 走重建
    }
    if (CacheKeys.NULL_MARKER.equals(cached)) {
        throw new BizException(ResultCode.PRODUCT_NOT_FOUND);   // 状态二：空值标记 → 直接 2001
    }
    try {
        return objectMapper.readValue(cached, SpuDetailVO.class);   // 状态三：命中
    } catch (JsonProcessingException e) {
        // 缓存损坏自愈：删除脏 key 并降级查库
        log.warn("[缓存] 反序列化失败，删除损坏缓存并降级查库：spuId={}", spuId);
        bucket.delete();
        return null;
    }
}
```

**为什么这么写**：缓存里其实存着三类值——正常 JSON、`__NULL__` 标记、以及极少数损坏数据（比如序列化格式迭代后的旧结构）。把"读取"设计成**三态 + 自愈**：损坏时删掉脏 key 降级查库，而不是抛异常把用户请求打挂。缓存是加速层，任何缓存侧异常都不该影响主流程。

### 1.1.3 写缓存：TTL 抖动 + 空值标记：`writeSpuDetailCache`

```java
private void writeSpuDetailCache(Long spuId, SpuDetailVO vo) {
    RBucket<String> bucket = redissonClient.getBucket(CacheKeys.SPU_DETAIL_PREFIX + spuId);
    if (vo == null) {
        // DB 不存在/已下架 → 写空值标记（防穿透第二层）
        long ttl = properties.getNullValueTtlSeconds() + randomJitter(60);   // 120s + 0~60s
        bucket.set(CacheKeys.NULL_MARKER, Duration.ofSeconds(ttl));
        return;
    }
    // 正常值：TTL = 基础值 + 随机抖动（防雪崩）
    long ttl = properties.getSpuDetailTtlSeconds() + randomJitter(properties.getSpuDetailJitterSeconds());
    bucket.set(toJson(vo), Duration.ofSeconds(ttl));                        // 1800s + 0~300s
}

private long randomJitter(long jitterSeconds) {
    return jitterSeconds <= 0 ? 0 : ThreadLocalRandom.current().nextLong(jitterSeconds + 1);
}
```

**为什么这么写**：

- **抖动公式**：若所有商品缓存同一时刻批量写入且 TTL 相同，它们会**同时过期**——过期瞬间全量请求打到 DB 就是雪崩。加一个 `[0, 300s]` 的随机偏移，把过期时间打散。
- **空值 TTL（120s）为什么远短于正常值（1800s）**：空值标记是"防御性"数据，宁可短一点——数据后来真被创建了（比如重新上架），最多 2 分钟就能恢复正常。空值的抖动（60s）防止"恶意批量查不存在的 id"这些标记又同时过期。
- **为什么用 `ThreadLocalRandom` 而不是 `Random`**：多线程下 `Random` 有 CAS 竞争，`ThreadLocalRandom` 无锁且更快，是并发场景的标准选择。

### 1.1.4 布隆过滤器：拦截 + 重建 + 补入

```java
/** 布隆过滤器判定。未预热（元素数为 0）时不拦截，避免误杀全部查询 */
private boolean mightExist(Long spuId) {
    RBloomFilter<Long> bloom = bloomFilter();
    if (bloom.count() == 0) {
        return true;
    }
    return bloom.contains(spuId);
}

private RBloomFilter<Long> bloomFilter() {
    RBloomFilter<Long> bloom = redissonClient.getBloomFilter(CacheKeys.SPU_BLOOM);
    bloom.tryInit(BLOOM_EXPECTED_INSERTIONS, BLOOM_FALSE_PROBABILITY);   // tryInit 幂等：已初始化返回 false 不抛异常
    return bloom;
}

/** 新增商品上架时补入布隆过滤器（否则新 spuId 会被布隆误判为不存在） */
public void addSpuToBloom(Long spuId) {
    bloomFilter().add(spuId);
    log.info("[缓存] 布隆过滤器已补入：spuId={}", spuId);
}
```

关键参数：容量 10,000、误判率 0.01（`BLOOM_EXPECTED_INSERTIONS` / `BLOOM_FALSE_PROBABILITY`）。

**为什么这么写**：

- **误判率 1% 可接受**：布隆误判方向只有一种——"不存在→可能存在"（假阳性），后果仅是多查一次 DB，再由空值缓存兜底。所以 1% 完全够用；调更低（如 0.001）会让位图更大、哈希次数更多，性价比反而下降。
- **`tryInit` 而不是 `init`**：`init` 对已存在的 key 会抛异常，`tryInit` 幂等返回 false——服务每次启动、每次调用 `bloomFilter()` 都可以放心执行。
- **为什么要有 `addSpuToBloom`**：布隆是"只进不出"的结构，新增 SPU 若不补入，会被误判为"不存在"直接 2001 拒掉。所以**管理端新增商品成功后必须补入**（代码在 `AdminProductService.createSpu` 链路调用）。

### 1.1.5 防击穿：互斥锁的姿势

```java
private boolean tryLock(RLock lock) {
    try {
        return lock.tryLock(properties.getLockWaitMillis(), properties.getLockLeaseMillis(),
                TimeUnit.MILLISECONDS);           // tryLock(等待 500ms, 租约 10s)
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();      // 恢复中断标记（不要吞掉中断状态）
        return false;
    }
}
// finally 中释放：if (lock.isHeldByCurrentThread()) { lock.unlock(); }
```

**为什么这么写**：

- **等待 500ms 短等待**：等不到就降级直查 DB（见 1.1.1 的竞争失败分支）——把"等待"控制在一次性窗口内，不让请求堆积。
- **租约 10s**：持锁线程如果宕机/卡死，Redisson 看门狗机制配合租约保证锁最终自动释放，**不会死锁**。
- **`isHeldByCurrentThread()` 判断后才 unlock**：防止极端情况下（锁超时自动释放后又被别人拿到）误释放他人的锁。Redisson 的 `unlock` 本身会校验持有者并抛 `IllegalMonitorStateException`，这个判断是防御性双保险。
- **`InterruptedException` 恢复中断标记**：多线程规范——捕获中断异常后必须 `Thread.currentThread().interrupt()` 把中断状态还回去，不能吞掉。

### 1.1.6 写路径：延迟双删的投递端：`evictSpuDetail`

```java
public void evictSpuDetail(Long spuId, boolean doubleDelete) {
    String key = CacheKeys.SPU_DETAIL_PREFIX + spuId;
    redissonClient.getKeys().delete(key);                    // 第一次删除：立即
    log.info("[缓存] 已删除：key={}", key);
    if (doubleDelete) {
        cacheEvictWorker.scheduleEvict(key, properties.getDoubleDeleteDelayMillis());  // 第二次：延迟 2000ms
    }
}
```

调用时机：管理端更新/上下架商品——**先改 DB，再调这个方法**。

**为什么删两次**：更新 DB 后立刻删缓存，但删除的瞬间可能有读请求**刚从 DB 读到旧值**（读发生在写提交前、回填发生在删除后）——脏数据会存活整个 TTL（30 分钟）。延迟 2 秒再删一次，把窗口期回填的脏数据清掉。

**为什么是"删缓存"而不是"更新缓存"**：更新缓存有并发写覆盖问题（两个写请求乱序回写，后写的可能是旧值）；而 Cache Aside 标准做法就是删，下次读时重建，且重建值是聚合计算结果，更省事更安全。

### 1.1.7 延迟删除 worker：ZSET 自研延迟队列（完整代码）

> 文件：`eshop-product/.../cache/CacheEvictWorker.java`

```java
@Component
@RequiredArgsConstructor
public class CacheEvictWorker implements SmartLifecycle {          // ① 跟随 Spring 生命周期启停

    public static final String EVICT_ZSET_KEY = "product:queue:cache-evict";
    private static final long SCAN_INTERVAL_MILLIS = 200;

    private final RedissonClient redissonClient;
    private volatile boolean running = false;
    private Thread workerThread;

    @Override
    public void start() {                                          // ② 启动一个守护线程
        running = true;
        workerThread = new Thread(this::loop, "cache-evict-worker");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("[缓存] 延迟删除 worker 已启动：zset={}", EVICT_ZSET_KEY);
    }

    @Override
    public void stop() {                                           // ③ 随应用优雅停止
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** 投递延迟删除任务：key 将在 delayMillis 后被删除 */
    public void scheduleEvict(String key, long delayMillis) {
        delayedSet().add(System.currentTimeMillis() + delayMillis, key);   // ZADD score=到期时间戳
        log.info("[缓存] 已投递延迟删除任务：key={}，{}ms 后执行", key, delayMillis);
    }

    /** 消费循环：扫描到期任务（score <= now）并执行删除 */
    private void loop() {
        while (running) {
            try {
                RScoredSortedSet<String> zset = delayedSet();
                Double earliestScore = zset.firstScore();          // 看最早的到期时间
                if (earliestScore == null) {
                    Thread.sleep(SCAN_INTERVAL_MILLIS);
                    continue;
                }
                long waitMillis = earliestScore.longValue() - System.currentTimeMillis();
                if (waitMillis > 0) {
                    Thread.sleep(Math.min(waitMillis, SCAN_INTERVAL_MILLIS));  // 没到期就睡到最近到期点
                    continue;
                }
                String key = zset.pollFirst();                     // ④ ZPOPMIN 原子取出
                if (key == null) {
                    continue;
                }
                long deleted = redissonClient.getKeys().delete(key);
                log.info("[缓存] 延迟删除执行完成：key={}，删除 {} 个", key, deleted);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // 单条任务失败不影响循环：缓存删除失败的后果仅是脏数据滞留至 TTL 到期
                log.warn("[缓存] 延迟删除执行失败：{}", e.getMessage());
                try {
                    Thread.sleep(SCAN_INTERVAL_MILLIS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.info("[缓存] 延迟删除 worker 已退出");
    }

    private RScoredSortedSet<String> delayedSet() {
        return redissonClient.getScoredSortedSet(EVICT_ZSET_KEY);
    }
}
```

**怎么写的**：ZSET 当延迟队列——`member = 待删除的缓存 key`，`score = 执行时间戳（毫秒）`。投递 = `ZADD`；消费 = 循环 `firstScore()` 看最近任务，到期就 `ZPOPMIN`（原子取出）后 `DEL` 对应 key。

**为什么这么写**：

- **为什么不用 Redisson 的 `RDelayedQueue`**：自 3.52 起被官方标记**废弃**，推荐替代品 `RReliableQueue` 是 **Redisson PRO 商业授权**功能（收费）。ZSET 是 Redis 原生能力，无版本/授权依赖。
- **为什么不用 MQ 延迟消息**（项目本身就有 RocketMQ）：性能语义不同。缓存删除**高频、低价值、可容忍丢失**（丢了最多脏数据存活到 TTL），为它走 MQ 太重；ZSET 直接复用现有 Redis，零新增组件。RocketMQ 留给超时关单这种"一单一次、必须可靠"的场景——**可靠性分级用不同工具**。
- **天然优点（被问收益时答）**：① 任务在 Redis 里，**服务重启不丢**（重启后循环继续消费）；② 同 key 重复投递 `ZADD` 覆盖 score，**自动合并不堆积**；③ `ZPOPMIN` 原子取出，多实例部署也不会重复执行。
- **`SmartLifecycle` 为什么不用 `@PostConstruct`**：`SmartLifecycle` 提供 start/stop 成对生命周期，应用关闭时能**优雅停线程**（`stop()` 里置 running=false + interrupt）；`@PostConstruct` 只有启动没停止。
- **200ms 扫描间隔的取舍**：不是纯轮询——`firstScore()` 拿到最近到期时间后 `sleep(min(wait, 200ms))`，**有任务时精确睡到到期点**，无任务时按 200ms 轻量空转。比"每秒无脑扫"省 Redis 调用。

### 1.1.8 预热：防"上线瞬间全量打库"

```java
// ProductCacheService.preheat()
public String preheat() {
    List<Long> spuIds = productService.listOnlineSpuIds();

    RBloomFilter<Long> bloom = bloomFilter();              // ① 重建布隆过滤器
    for (Long spuId : spuIds) {
        bloom.add(spuId);
    }

    int written = 0;                                       // ② 商品详情批量落缓存（已存在则跳过）
    for (Long spuId : spuIds) {
        RBucket<String> bucket = redissonClient.getBucket(CacheKeys.SPU_DETAIL_PREFIX + spuId);
        if (bucket.isExists()) {
            continue;
        }
        writeSpuDetailCache(spuId, productService.loadSpuDetail(spuId));
        written++;
    }
    ...
}
```

```java
// runner/CachePreheatRunner.java（完整）
@Component
@RequiredArgsConstructor
public class CachePreheatRunner implements ApplicationRunner {

    private final ProductCacheService productCacheService;
    private final ProductCacheProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isPreheatEnabled()) {
            log.info("[缓存] 启动预热已关闭（eshop.cache.preheat-enabled=false）");
            return;
        }
        Thread thread = new Thread(() -> {           // 异步线程执行
            try {
                productCacheService.preheat();
            } catch (Exception e) {
                log.warn("[缓存] 启动预热失败（不影响服务运行）：{}", e.getMessage());
            }
        }, "cache-preheat");
        thread.setDaemon(true);
        thread.start();
    }
}
```

**为什么这么写**：服务重启后缓存是空的，若不加预热，上线瞬间所有流量穿透到 DB。预热把"热点数据铺缓存 + 布隆重建"提前到启动后异步执行——**失败也不阻断启动**（读路径全程可回源 DB，预热只是加速）。

### 1.1.9 Redisson 手动装配

```java
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")            // 应用关闭时释放连接
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host:127.0.0.1}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database) {
        Config config = new Config();
        config.setCodec(new JsonJacksonCodec());   // 统一 JSON 编解码：Redis 里可读、跨语言可查
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(8);
        if (StringUtils.hasText(password)) {
            config.useSingleServer().setPassword(password);
        }
        return Redisson.create(config);
    }
}
```

**为什么手动装配而不是 `redisson-spring-boot-starter`**：连接参数、编解码器、连接池**全部显式可见**，便于讲解和调优；同时复用 `spring.data.redis` 配置前缀（与 cart 服务一致），不引入第二套配置体系。`JsonJacksonCodec` 让布隆过滤器元数据、延迟队列元素在 Redis 里都是可读 JSON，排查方便。

### 1.1.10 缓存参数一览（改参数不用改代码）

| 配置项 | 值 | 含义 |
|---|---|---|
| `spu-detail-ttl-seconds` | 1800 | 详情基础 TTL（30 分钟） |
| `spu-detail-jitter-seconds` | 300 | TTL 抖动上限（防雪崩） |
| `category-ttl-seconds` | 3600 | 分类列表 TTL |
| `null-value-ttl-seconds` | 120 | 空值标记 TTL |
| `lock-wait-millis` | 500 | 互斥锁等待上限 |
| `lock-lease-millis` | 10000 | 互斥锁租约 |
| `double-delete-delay-millis` | 2000 | 延迟双删第二次延迟 |
| `preheat-enabled` | true | 启动预热开关 |

绑定类：`ProductCacheProperties`（`@ConfigurationProperties(prefix = "eshop.cache")`）。

---

## 1.2 库存 Lua 预扣闸门（stock）

### 1.2.1 架构：两层防超卖

```
并发下单流量
   │
   ▼
【第一层】Redis Lua 预扣闸门（StockRedisGate）—— 热点流量在闸门处拦截/放行
   │  ├─ SUCCESS      扣减成功 → 继续
   │  ├─ INSUFFICIENT 余量不足 → 直接拒绝（3002），不打 DB ← 抗并发的关键
   │  └─ BYPASS       键不存在/Redis 异常 → 放行，由 DB 兜底
   ▼
【第二层】DB 乐观锁（version 字段 + 重试 3 次）—— 最终账本，绝对不超卖
```

**Redis 是"准入闸门"不是"账本"**——这句话是整个设计的灵魂。

### 1.2.2 Lua 脚本（全文 + 逐行）

> 文件：`eshop-stock/src/main/resources/lua/stock_deduct.lua`

```lua
-- KEYS[1] = stock:sku:{skuId}   Redis 中的可售余量（初始由 DB available 预热）
-- ARGV[1] = 本次预扣数量
local stock = redis.call('GET', KEYS[1])
if not stock then
    return -1                                    -- -1: 未预热 → 调用方降级放行
end
local available = tonumber(stock)
local quantity = tonumber(ARGV[1])
if available < quantity then
    return -2                                    -- -2: 余量不足 → 直接拒绝
end
return redis.call('DECRBY', KEYS[1], quantity)   -- >=0: 成功，返回扣减后余量
```

**为什么必须 Lua**：`GET` 判断余量 + `DECRBY` 扣减是两步——如果拆成两条独立命令，两个并发请求可能**都读到 `available=1`、都判断通过、各扣一次** → 超扣。Lua 脚本在 Redis 单线程内**整体作为一个命令原子执行**，天然无竞态。

**回补脚本为什么要判 EXISTS**（`stock_restore.lua` 全文）：

```lua
if redis.call('EXISTS', KEYS[1]) == 0 then
    return -1                                    -- 键不存在 → 不凭空创建
end
return redis.call('INCRBY', KEYS[1], ARGV[1])
```

键不存在时不创建——否则"未预热降级放行"的 SKU 会被回补操作**污染出假余量**（Redis 显示有货、DB 其实没有）。

### 1.2.3 脚本注册（把 lua 文件变成可注入的 Bean）

```java
@Configuration
public class StockRedisConfig {

    @Bean
    public DefaultRedisScript<Long> stockDeductScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/stock_deduct.lua"));   // classpath 加载
        script.setResultType(Long.class);                                    // 返回类型 Long
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> stockRestoreScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/stock_restore.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
```

**为什么这么写**：`DefaultRedisScript` 支持 `EVALSHA` 优化——Spring Data Redis 首次执行时 `SCRIPT LOAD` 到 Redis 缓存，之后走 SHA1 只发脚本摘要，不用每次传全文。脚本文件放 `resources/lua/` 与代码解耦，语法独立可测。

### 1.2.4 闸门类（完整代码 + 三态设计）

> 文件：`eshop-stock/.../cache/StockRedisGate.java`

```java
@Component
@RequiredArgsConstructor
public class StockRedisGate {

    public static final String KEY_PREFIX = "stock:sku:";      // 值 = 可售余量

    /** 预扣结果三态 */
    public enum DeductResult {
        SUCCESS,        // 预扣成功（Redis 已扣减，失败路径需回滚）
        INSUFFICIENT,   // 余量不足（直接拒绝，不打 DB）
        BYPASS          // 键不存在 / Redis 异常 → 降级放行（DB 乐观锁兜底）
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> stockDeductScript;
    private final DefaultRedisScript<Long> stockRestoreScript;

    public DeductResult deduct(Long skuId, int quantity) {
        try {
            Long result = stringRedisTemplate.execute(stockDeductScript,
                    List.of(key(skuId)), String.valueOf(quantity));
            if (result == null || result == -1L) {
                return DeductResult.BYPASS;                    // 未预热
            }
            if (result == -2L) {
                log.info("[库存] Redis 预扣拒绝（余量不足）: skuId={}, quantity={}", skuId, quantity);
                return DeductResult.INSUFFICIENT;              // 拒绝
            }
            log.debug("[库存] Redis 预扣成功: skuId={}, quantity={}, remain={}", skuId, quantity, result);
            return DeductResult.SUCCESS;
        } catch (Exception e) {
            // Redis 是加速层而非依赖层：异常时降级放行，交易由 DB 兜底
            log.warn("[库存] Redis 预扣降级放行: skuId={}, err={}", skuId, e.getMessage());
            return DeductResult.BYPASS;
        }
    }

    /** 加回（回滚预扣 / 取消回补共用）。best-effort，异常不阻断主流程 */
    public void increase(Long skuId, int quantity) {
        try {
            Long result = stringRedisTemplate.execute(stockRestoreScript,
                    List.of(key(skuId)), String.valueOf(quantity));
            if (result != null && result == -1L) {
                log.debug("[库存] Redis 加回跳过（键不存在）: skuId={}", skuId);
            } else {
                log.debug("[库存] Redis 加回成功: skuId={}, quantity={}", skuId, quantity);
            }
        } catch (Exception e) {
            log.warn("[库存] Redis 加回失败（不阻断主流程）: skuId={}, err={}", skuId, e.getMessage());
        }
    }

    /** 以 DB 值重置 Redis（预热 / 校准） */
    public void sync(Long skuId, int available) {
        try {
            stringRedisTemplate.opsForValue().set(key(skuId), String.valueOf(available));
        } catch (Exception e) {
            log.warn("[库存] Redis 校准失败: skuId={}, err={}", skuId, e.getMessage());
        }
    }

    /** 读取 Redis 当前余量（核对用；null = 键不存在） */
    public Long get(Long skuId) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key(skuId));
            return value == null ? null : Long.valueOf(value);
        } catch (Exception e) {
            log.warn("[库存] Redis 读取失败: skuId={}, err={}", skuId, e.getMessage());
            return null;
        }
    }

    public String key(Long skuId) {
        return KEY_PREFIX + skuId;
    }
}
```

**为什么这么写（三态是整个设计的核心）**：

- `INSUFFICIENT` 直接拒绝——**热点流量在闸门处就被挡掉，根本不进 DB**，这是抗并发的关键（1 万个人抢 100 件库存，9900 个请求在 Redis 层返回 3002）。
- `BYPASS` 覆盖两种降级：未预热（键不存在）和 Redis 异常。**每个方法都 try-catch 吞异常**——Redis 挂了交易照样能走（DB 乐观锁兜底），只是少了加速层。这是"**Redis 是加速层不是依赖层**"的代码级体现。
- `increase` 是 best-effort——回滚失败不抛异常，最多造成 Redis 值与 DB 漂移（方向安全：少卖不超卖），由预热对账收敛。

### 1.2.5 与 DB 的整合：`StockService.lock`（双层 + 补偿回滚）

```java
/** 锁定库存：available -= n，locked += n（幂等；库存不足抛 3002） */
@Transactional(rollbackFor = Exception.class)
public void lock(StockOpRequest request) {
    validate(request);
    List<StockOpRequest.Item> deducted = new ArrayList<>();     // 记录已成功预扣的项（补偿用）
    try {
        for (StockOpRequest.Item item : request.getItems()) {
            if (logExists(request.getOrderNo(), item.getSkuId(), StockLog.TYPE_LOCK)) {
                continue;                                       // ① 幂等：流水已存在 → 跳过
            }
            // ② 第一层：Redis Lua 原子预扣
            StockRedisGate.DeductResult result = stockRedisGate.deduct(item.getSkuId(), item.getQuantity());
            if (result == StockRedisGate.DeductResult.INSUFFICIENT) {
                throw new BizException(ResultCode.STOCK_INSUFFICIENT);
            }
            if (result == StockRedisGate.DeductResult.SUCCESS) {
                deducted.add(item);                             // 记住它，失败时要还回去
            }
            // BYPASS → 降级放行，由第二层 DB 兜底
            // ③ 第二层：DB 乐观锁扣减（最终账本）
            lockOne(request.getOrderNo(), item);
        }
    } catch (Exception e) {
        // DB 由本地事务 / Seata 全局事务回滚；Redis 预扣在此补偿回滚（best-effort）
        for (StockOpRequest.Item item : deducted) {
            stockRedisGate.increase(item.getSkuId(), item.getQuantity());    // 补偿：Redis 还回去
        }
        throw e;
    }
}
```

**为什么这么写（补偿回滚是精髓）**：Redis 预扣成功后、后续 item 或 DB 扣减失败时——**Redis 的扣减不归 DB 事务管**（不同系统），必须手工补偿。`deducted` 列表就是为此存在的：凡是走到 `lockOne` 之前先记一笔，出错时逐个 `increase` 还回去。

**第二层：DB 乐观锁**（`lockOne` 关键段）：

```java
for (int attempt = 1; attempt <= LOCK_MAX_ATTEMPTS; attempt++) {     // 重试 3 次
    Stock stock = requireStock(skuId);
    if (stock.getAvailable() < quantity) {
        throw new BizException(ResultCode.STOCK_INSUFFICIENT);
    }
    int rows = stockMapper.update(null, new LambdaUpdateWrapper<Stock>()
            .set(Stock::getAvailable, stock.getAvailable() - quantity)
            .set(Stock::getLocked, stock.getLocked() + quantity)
            .set(Stock::getVersion, stock.getVersion() + 1)          // version + 1
            .eq(Stock::getSkuId, skuId)
            .eq(Stock::getVersion, stock.getVersion())               // CAS：WHERE version = 读到的值
            .ge(Stock::getAvailable, quantity));                     // 且 available 仍够（双保险）
    if (rows == 1) {
        insertLog(...);                                              // 记流水（幂等依据）
        return;
    }
    // rows == 0：版本冲突（并发修改），重试
}
```

**为什么这么写**：`WHERE version = ? AND available >= ?` 一条 UPDATE 完成"检查 + 扣减"——并发下只有一个事务能更新成功（rows=1），其余拿到 rows=0 重试。**即使 Redis 层完全失效放行所有流量，DB 层也不会超卖**——这就是"最终账本"的含义。重试 3 次仍失败才拒绝（大概率是真没库存了）。

**幂等的双重保障**：`logExists`（库存流水 `(order_no, sku_id, type)` 去重）+ SQL 的 CAS——下单失败重试、MQ 重投、人工重放，都安全。

### 1.2.6 回补：DB 与 Redis 同步加回（`restoreOne` 关键段）

```java
private void restoreOne(String orderNo, StockOpRequest.Item item) {
    ...
    if (stock.getLocked() < quantity) {
        // 无锁定量可回补（如锁定阶段已回滚）：收敛式跳过并留痕，保证幂等
        log.warn("库存回补跳过（无锁定量）: orderNo={}, skuId={}, locked={}, need={}", ...);
        insertLog(orderNo, skuId, StockLog.TYPE_RESTORE, 0, ..., "无锁定库存可回补，跳过");
        return;
    }
    stockMapper.update(null, new LambdaUpdateWrapper<Stock>()
            .set(Stock::getAvailable, stock.getAvailable() + quantity)   // available += n
            .set(Stock::getLocked, stock.getLocked() - quantity)         // locked -= n
            .set(Stock::getVersion, stock.getVersion() + 1)
            .ge(Stock::getLocked, quantity));
    insertLog(...);
    // 同步恢复 Redis 可售余量（best-effort；重复调用被上方流水去重拦截，不会重复加回）
    stockRedisGate.increase(skuId, quantity);
}
```

**为什么这么写**：回补 = 取消/关单时把锁定量还给可售。Redis 侧同步 `increase`（加回），保证两侧同增同减。**"无锁定量跳过"分支**处理收敛场景：如果锁定阶段就回滚过（没有 locked 可还），重复回补不能把 available 加多——跳过 + 留痕（流水记录 quantity=0），保证幂等。

### 1.2.7 预热 = 对账校准（关键认知）

```java
// StockService.preheatRedis()：以 DB 为准全量重建
public String preheatRedis() {
    List<Stock> stocks = stockMapper.selectList(null);
    for (Stock stock : stocks) {
        stockRedisGate.sync(stock.getSkuId(), stock.getAvailable());
    }
    ...
}
```

```java
// runner/StockPreheatRunner.java：启动时异步执行（完整）
@Component
@RequiredArgsConstructor
public class StockPreheatRunner implements ApplicationRunner {
    private final StockService stockService;

    @Override
    public void run(ApplicationArguments args) {
        Thread thread = new Thread(() -> {
            try {
                stockService.preheatRedis();
            } catch (Exception e) {
                log.warn("[库存] Redis 预热失败（不阻断启动，Lua 层降级放行）: {}", e.getMessage());
            }
        }, "stock-preheat");
        thread.setDaemon(true);
        thread.start();
    }
}
```

**漂移从哪来**：Seata 全局回滚走的是 undo_log（**不经过 stock 服务代码**），此时 Redis 的预扣没人补偿——Redis 值比 DB 少（方向偏严格：少卖不超卖）。

**为什么预热就能收敛**：`preheatRedis` 以 DB `available` 为唯一事实源全量覆盖 Redis——**每次重启自动校准一次**。生产环境可以加定时任务（如每 5 分钟对账），本项目用"重启收敛 + 手动 `/internal/stock/preheat` 接口"。

### 1.2.8 双侧核对接口（验证/排查用）

```java
public Map<String, Object> inspectRedis(Long skuId) {
    Stock stock = requireStock(skuId);
    Long redisValue = stockRedisGate.get(skuId);
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("skuId", skuId);
    map.put("dbAvailable", stock.getAvailable());
    map.put("redisValue", redisValue);
    map.put("consistent", redisValue != null && redisValue.intValue() == stock.getAvailable());
    return map;
}
```

访问 `GET /internal/stock/redis/{skuId}` 即可对比 DB 与 Redis 的差值，冒烟测试用它断言"两侧一致"。

---

## 1.3 购物车（cart）：Redis Hash 结构

> 文件：`eshop-cart/.../service/CartService.java`

**结构设计**：`key = cart:{userId}`（Hash），`field = skuId`，`value = CartItemState JSON`：

```java
public class CartItemState {
    private Integer quantity;    // 数量（1-99）
    private Boolean checked;     // 是否勾选
    private Long addTime;        // 加购时间戳（列表排序用）
}
```

**加购（读-改-写 + 续期）**：

```java
public void add(Long userId, CartAddRequest request) {
    Long skuId = request.getSkuId();
    int quantity = request.getQuantity() == null ? 1 : request.getQuantity();

    if (querySkuInfos(List.of(skuId)).isEmpty()) {       // ① Feign 校验 SKU 在架
        throw new BizException(ResultCode.SKU_NOT_FOUND);
    }

    String key = cartKey(userId);
    String field = String.valueOf(skuId);
    CartItemState state = parseState(getRaw(key, field));  // ② HGET 现有条目
    if (state == null) {
        state = new CartItemState(0, true, System.currentTimeMillis());
    }
    int current = state.getQuantity() == null ? 0 : state.getQuantity();
    state.setQuantity(Math.min(MAX_QUANTITY, current + quantity));   // ③ 累加封顶 99
    state.setChecked(true);                                          // 加购即勾选
    save(key, field, state);
}

/** 写入并续期（滑动过期） */
private void save(String key, String field, CartItemState state) {
    redisTemplate.opsForHash().put(key, field, serializeState(state));   // HSET
    redisTemplate.expire(key, CART_TTL);                                 // EXPIRE 30 天
}
```

**为什么这么写**：

- **为什么购物车放 Redis 不放 MySQL**：购物车是**高频读写、低价值、可容忍丢失**的数据（丢了就重新加）。写库会给 MySQL 增加大量无意义写压力；Redis Hash 内存操作性能高一个数量级，还能用 TTL 自动清理僵尸数据。
- **为什么 Hash 而不是 String 存整个 JSON**：Hash 的每个 field 是独立条目——增删改**单个 SKU 只动一个 field**（`HSET`/`HDEL` 是原子单命令）；String 存整个数组必须"读出全量 → 改 → 写回全量"，并发下容易互相覆盖，且每次都传全量数据浪费带宽。
- **每次写入 `expire` 续期 30 天（滑动过期）**：给"活跃购物车"保命——只要用户 30 天内动过购物车，数据就不丢；彻底不活跃的自动释放，不需要清理任务。
- **展示信息为什么不进 Redis**：名称/价格/图片由 Feign 实时从商品服务取（`querySkuInfos`）——**价格永不过期**（用户看到的是最新价），Redis 里只存"操作状态"（数量/勾选）。这是"**存储与展示分离**"的取舍。
- **商品已下架的条目为什么跳过保留**（`list` 中 `info == null → continue`）：不删用户的 Redis 数据（下架可能恢复），展示层跳过即可，避免"用户没操作的条目被系统静默删除"。

---

## 二、RocketMQ：延迟消息超时关单（order）

### 2.1 为什么不用定时任务扫表

扫表方案：每分钟 `SELECT * FROM t_order WHERE status=0 AND create_time < now()-30min`。问题：① 精度取决于扫描频率；② 订单量大了全表扫描压力大；③ 业务代码里到处是调度器。**延迟消息**让"到点触发"由 MQ 保证，业务代码只剩一个消费者，精度到秒、无扫描开销。

### 2.2 链路全景

```
下单成功 ──① syncSend(topic, orderNo, delayLevel=16)──▶ RocketMQ（30分钟后投递）
                                                              │ ② 到期投递
消费端 ◀──────────────────────────────────────────────────────┘
  └─ handleTimeout(orderNo)：
       查订单 → status != 待支付 ? 静默跳过（幂等）
              → CAS 流转 0→5（并发刚被支付/取消时 CAS 失败，幂等跳过）
              → 回补库存（库存侧按 (order_no,sku_id,type) 流水幂等）
```

### 2.3 接入配置（yaml + pom）

```yaml
# eshop-order/src/main/resources/application.yml
rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: eshop-order-producer
    send-message-timeout: 3000          # 同步发送超时 3s
    retry-times-when-send-failed: 2     # 发送失败重试 2 次
eshop:
  mq:
    # 延迟级别（1s 5s 10s 30s 1m 2m ... 30m 1h 2h → 1..18）；16 = 30 分钟
    # 本地演示可加启动参数 --eshop.mq.order-timeout-delay-level=3（10 秒）快速验证
    order-timeout-delay-level: 16
```

```xml
<!-- eshop-order/pom.xml -->
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>   <!-- 2.3.6（父 pom 管理） -->
</dependency>
```

### 2.4 主题与消费组常量

```java
public final class OrderMqConstants {
    public static final String TOPIC_ORDER_TIMEOUT = "eshop-order-timeout";
    public static final String GROUP_ORDER_TIMEOUT_CONSUMER = "eshop-order-timeout-consumer";
    private OrderMqConstants() { }
}
```

**为什么抽常量类**：主题/消费组字符串在生产者、消费者、文档三处引用——抽常量防拼写错误（拼错 topic 是最难查的 MQ bug，消息静默发到不存在的主题）。

### 2.5 生产者（完整代码）

```java
@Component
@RequiredArgsConstructor
public class OrderTimeoutProducer {

    private static final long SEND_TIMEOUT_MILLIS = 3000;
    private final RocketMQTemplate rocketMQTemplate;

    @Value("${eshop.mq.order-timeout-delay-level:16}")
    private int delayLevel;

    /** 发送超时关单延迟消息（同步发送；失败由调用方决定处理策略） */
    public void send(String orderNo) {
        Message<String> message = MessageBuilder.withPayload(orderNo).build();
        rocketMQTemplate.syncSend(OrderMqConstants.TOPIC_ORDER_TIMEOUT, message,
                SEND_TIMEOUT_MILLIS, delayLevel);
        log.info("超时关单消息已发送: orderNo={}, delayLevel={}", orderNo, delayLevel);
    }
}
```

**为什么这么写**：

- **`syncSend`（同步）而不是 `sendOneWay`/`async`**：下单链路需要"消息确实发出去了"的确定性；同步等 broker ACK（3s 超时），失败立即走 catch 分支。异步/单向发送虽然快，但丢失静默无声。
- **消息体只放订单号**：不放快照——**消费端按订单号查最新状态**（DB 才是事实源）。消息可能延迟、重复、乱序，传快照会有"用什么时间点的数据"的歧义；只传订单号让幂等天然成立。
- **延迟级别走配置**：生产语义 16=30 分钟；测试/冒烟用启动参数 `--eshop.mq.order-timeout-delay-level=3`（10 秒）——**不用改代码就能把 30 分钟压成 10 秒验证**。

### 2.6 下单链路中的发送点（`OrderService.create` 摘录）

```java
// 锁定库存（Seata 全局事务分支）……（略）

// 发送超时关单延迟消息（P2）：到期未支付 → 自动关闭 + 回补库存
// 发送失败不阻断下单（订单仍可支付/手动取消），仅留错误日志；生产级可加「本地消息表 + 定时补偿」
try {
    orderTimeoutProducer.send(orderNo);
} catch (Exception e) {
    log.error("超时关单消息发送失败（订单不受影响）: orderNo={}", orderNo, e);
}

// 清理购物车中已下单条目（尽力而为，不影响下单结果）
try {
    ResultHelper.unwrap(cartClient.batchRemove(userId, ...));
} catch (Exception e) {
    log.warn("下单后清理购物车失败（不影响订单）: orderNo={}, userId={}", orderNo, userId, e);
}
```

**为什么这么写（容错分级）**：

- **发送失败不阻断下单**：消息丢了最坏后果是"这单不会被自动关闭"（仍可支付/手动取消）——为它回滚整笔订单不划算。**核心链路（下单）与非核心链路（定时关单）解耦**。
- **发送点在锁库存之后**：确保"发给 MQ 的单"都是库存已锁定的单；若在锁库存前发，库存失败回滚后还得处理"消息已发"的尴尬。
- **生产级补强（被追问时答）**：可加"本地消息表 + 定时补偿"（事务消息方案），本项目演示场景用"日志留痕 + 订单可手动取消"兜底。

### 2.7 消费者（完整代码）

```java
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = OrderMqConstants.TOPIC_ORDER_TIMEOUT,
        consumerGroup = OrderMqConstants.GROUP_ORDER_TIMEOUT_CONSUMER)
public class OrderTimeoutConsumer implements RocketMQListener<String> {

    private final OrderService orderService;

    @Override
    public void onMessage(String orderNo) {
        log.info("收到超时关单延迟消息: orderNo={}", orderNo);
        orderService.handleTimeout(orderNo);
    }
}
```

**为什么这么写**：

- **消费失败（系统异常）时让异常向上抛**：`onMessage` 不自行 catch 系统异常——抛出去 RocketMQ **自动重试**（默认 16 次、指数退避）。业务上"无需处理"的情况（订单已支付/已取消）在下游 `handleTimeout` 里静默 return，不算失败。
- **消费组名固定**：扩容多实例时同一消费组自动负载均衡（每个消息只被一个实例消费）。
- **消费端不做复杂逻辑**：只转发到 `handleTimeout`——消费线程宝贵，逻辑放 service 层，也方便单测。

### 2.8 关单核心：`handleTimeout`（幂等三连）

```java
public void handleTimeout(String orderNo) {
    Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
            .eq(Order::getOrderNo, orderNo));
    if (order == null) {
        log.warn("超时关单：订单不存在，忽略: orderNo={}", orderNo);     // ① 不存在 → 忽略
        return;
    }
    if (order.getStatus() != OrderStatus.PENDING_PAY) {
        log.info("超时关单：订单非待支付状态，忽略: orderNo={}, status={}", orderNo, order.getStatus());  // ② 非待支付 → 忽略
        return;
    }
    try {
        transactStatus(order, OrderStatus.CLOSED, OrderStatus.OPERATOR_SYSTEM, 0L,
                "超时未支付，系统自动关闭", true);                       // ③ CAS 流转 0→5
    } catch (BizException e) {
        // CAS 失败：并发下订单刚被支付/取消，幂等跳过
        log.info("超时关单：状态已被并发流转，忽略: orderNo={}", orderNo);
        return;
    }

    // 回补库存（库存侧按 (order_no,sku_id,type) 流水幂等；失败留痕待补偿）
    StockOpRequest stockOp = buildStockOp(orderNo, listItems(orderNo));
    try {
        ResultHelper.unwrap(stockClient.restore(stockOp));
    } catch (Exception e) {
        log.error("超时关单回补库存失败，待补偿: orderNo={}", orderNo, e);
    }
    log.info("订单超时关闭完成: orderNo={}", orderNo);
}
```

**幂等三连（消息重复投递/消费重试都安全的原因）**：

1. **查状态不满足 → 静默返回**（第一次拦截）；
2. **CAS 流转**（`UPDATE ... WHERE status=0`）——并发下"刚被支付"和"超时关单"同时发生，只有一个成功；CAS 失败 = 已被处理（第二次拦截）；
3. **回补库存下游再幂等**（库存流水 `(order_no, sku_id, type)` 去重，第三次保险）。

**为什么回补失败只记日志**：订单已经关闭（主状态机完成），库存回补是补偿动作——失败留痕，库存流水表支持对账补偿（宁可"库存暂时虚高"，也**不会少卖**）。

### 2.9 失败语义表（被问"挂了怎么办"时照这张答）

| 故障点 | 行为 | 为什么安全 |
|---|---|---|
| ① 发送失败 | try-catch 吞掉，**不阻断下单**，留 error 日志 | 订单照常可支付/手动取消；生产级可加"本地消息表+定时补偿" |
| ② 消费异常 | 抛异常 → RocketMQ **自动重试**（默认 16 次） | 重试先查状态，已关/已付幂等跳过 |
| ③ 回补失败 | error 日志留痕 | 库存流水表支持对账补偿；不会少卖只会"库存暂时虚高" |
| ④ 消息重复投递 | 幂等三连全部挡住 | 查状态 + CAS + 下游流水去重 |
| ⑤ 服务重启 | 延迟消息在 broker 持久化（CommitLog） | 重启后到期照常投递 |

### 2.10 延迟级别速查

RocketMQ 延迟级别 1~18：`1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h`。
项目默认 **16（30 分钟）**；压测/冒烟用 **3（10 秒）** 快速验证（`--eshop.mq.order-timeout-delay-level=3`）。

---

## 三、面试问答速查

**Q1：缓存穿透/击穿/雪崩分别怎么解的？**
A：穿透——布隆前置拦截 + 空值缓存兜底（两层，布隆误判 1% 由空值缓存接住）；击穿——Redisson 互斥锁 + 双检，锁外线程降级直查 DB 不写缓存；雪崩——TTL 加 0~300s 随机抖动。布隆在 Redis 重启清空后自动放行（count=0 时不拦截），防止误杀全量请求。

**Q2：缓存和数据库一致性怎么保证的？**
A：Cache Aside 删缓存模式——更新 DB 后删除缓存（不更新缓存，避免并发写覆盖），再加**延迟双删**：2 秒后 ZSET 延迟队列再删一次，清掉"读请求把更新前旧值回填"窗口期的脏数据。延迟队列没用 RDelayedQueue（官方已废弃），用 ZSET 自实现：member=key、score=执行时间戳，ZPOPMIN 原子消费，重启不丢、同 key 投递自动合并。

**Q3：库存为什么不直接扣 DB？**
A：双层防超卖。第一层 Redis Lua 闸门（GET 判断 + DECRBY 扣减原子完成，余量不足直接拒绝**不打 DB**），扛住热点并发；第二层 DB 乐观锁（version CAS + 重试 3 次）是最终账本。Redis 是准入闸门不是账本——挂了/未预热就 BYPASS 放行，由 DB 兜底，可用性优先、方向安全（少卖不超卖）。

**Q4：Lua 脚本解决了什么问题？**
A：判断余量和扣减是两步，拆开发的话并发下会双双通过判断导致超扣。Lua 脚本在 Redis 单线程内原子执行，天然无竞态。另外回补脚本判 EXISTS 再 INCRBY，防止给未预热 SKU 凭空造余量。

**Q5：Redis 与 DB 的库存数据不一致怎么办？**
A：正常链路同步增减；Seata 全局回滚这类不经过 stock 服务的路径会造成短暂漂移（方向偏严格，少卖不超卖）；解法是预热即对账——以 DB 为准全量重建 Redis 闸门，启动时自动收敛，生产可加定时对账。

**Q6：为什么用 RocketMQ 延迟消息做超时关单，而不是定时任务扫表？**
A：扫表精度取决于扫描频率、大表扫描有压力、还要到处挂调度器；延迟消息到秒级精度、到点投递，业务侧只剩一个幂等消费者。

**Q7：消息丢了/消费者挂了/重复消费怎么办？**
A：发送失败不阻断下单（订单仍可支付/手动取消，生产加本地消息表补偿）；消费失败抛异常由 MQ 自动重试，重试前先查订单状态保证幂等；重复消费同理——查状态 + CAS 流转，CAS 失败即已被处理。

**Q8：消息体为什么只放订单号？**
A：消费端按订单号查 DB 最新状态而不是信消息快照——消息可能延迟、重复、乱序，DB 才是事实源；只传订单号让幂等天然成立。

**Q9：商品缓存删除为什么不用 MQ，而用 ZSET 自造延迟队列？**
A：可靠性分级。缓存删除高频、低价值、可容忍丢失（丢了最多脏数据存活到 TTL 到期），走 MQ 太重；ZSET 直接复用 Redis，零新增组件，还自带"重启不丢 + 同 key 合并"两个特性。RocketMQ 留给"每单必须可靠"的超时关单。

**Q10：购物车为什么放 Redis 不放 MySQL？**
A：购物车高频读写、低价值、可容忍丢失。写库会给 MySQL 大量无意义写压力；Redis Hash 支持单字段原子操作（HSET/HDEL），TTL 自动清理僵尸数据。展示信息（价格/图）不存 Redis，由 Feign 实时取——保证价格永不过期。

**Q11：Redisson 的锁是怎么防死锁的？**
A：`tryLock(等待 500ms, 租约 10s)` 带租约——持锁线程宕机后锁到期自动释放；释放前用 `isHeldByCurrentThread()` 校验，防止误释放他人的锁；等不到锁的线程直接降级查 DB（不堆积等待）。

**Q12：缓存里的值被改坏了（序列化不兼容）会怎样？**
A：读取端做了三态容错——`readSpuDetailCache` 反序列化失败时删掉损坏 key 并降级查库（自愈）；写入端重新回填新格式。缓存是加速层，任何异常都不影响主流程。

---

> 维护提示：本文档随代码演进更新；`notes-seata.md` 是配套的事务篇（Seata AT 讲解）。
