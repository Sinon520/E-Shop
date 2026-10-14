package com.eshop.product.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 缓存延迟删除 worker（延迟双删的"第二次删除"）
 *
 * <pre>
 * 实现：Redis ZSET 延迟队列（member = 待删除缓存 key，score = 执行时间戳毫秒）。
 *   - 投递：ZADD key executeAtMillis member
 *   - 消费：扫描 score <= now 的到期元素，原子取出（ZPOPMIN）后删除对应缓存 key
 *
 * 为什么不用 Redisson RDelayedQueue：其自 3.52 起被官方标记废弃
 * （推荐替代品 RReliableQueue 为 Redisson PRO 授权功能）；
 * ZSET 为 Redis 原生能力，无版本/授权依赖，且天然具备：
 *   - 重启不丢（任务数据在 Redis，重启后继续消费）
 *   - 重复投递自动合并（同 key 覆盖 score，不会任务堆积）
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictWorker implements SmartLifecycle {

    /** 延迟删除任务队列：member=缓存 key，score=执行时间戳（毫秒） */
    public static final String EVICT_ZSET_KEY = "product:queue:cache-evict";

    /** 到期任务扫描间隔（毫秒） */
    private static final long SCAN_INTERVAL_MILLIS = 200;

    private final RedissonClient redissonClient;

    private volatile boolean running = false;
    private Thread workerThread;

    @Override
    public void start() {
        running = true;
        workerThread = new Thread(this::loop, "cache-evict-worker");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("[缓存] 延迟删除 worker 已启动：zset={}", EVICT_ZSET_KEY);
    }

    @Override
    public void stop() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 投递延迟删除任务：key 将在 delayMillis 后被删除
     * <p>
     * 同一 key 重复投递自动合并（覆盖 score），不会重复堆积任务。
     */
    public void scheduleEvict(String key, long delayMillis) {
        delayedSet().add(System.currentTimeMillis() + delayMillis, key);
        log.info("[缓存] 已投递延迟删除任务：key={}，{}ms 后执行", key, delayMillis);
    }

    /** 消费循环：扫描到期任务（score <= now）并执行删除 */
    private void loop() {
        while (running) {
            try {
                RScoredSortedSet<String> zset = delayedSet();
                Double earliestScore = zset.firstScore();
                if (earliestScore == null) {
                    Thread.sleep(SCAN_INTERVAL_MILLIS);
                    continue;
                }
                long waitMillis = earliestScore.longValue() - System.currentTimeMillis();
                if (waitMillis > 0) {
                    Thread.sleep(Math.min(waitMillis, SCAN_INTERVAL_MILLIS));
                    continue;
                }
                // 已到期：原子取出（ZPOPMIN）并删除对应缓存 key
                String key = zset.pollFirst();
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
