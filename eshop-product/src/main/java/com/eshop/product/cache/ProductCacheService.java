package com.eshop.product.cache;

import com.eshop.common.exception.BizException;
import com.eshop.common.result.ResultCode;
import com.eshop.product.config.ProductCacheProperties;
import com.eshop.product.service.ProductService;
import com.eshop.product.vo.CacheInspectVO;
import com.eshop.product.vo.CategoryVO;
import com.eshop.product.vo.SpuDetailVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 商品缓存服务：Cache Aside + 读路径三件套防护 + 写路径延迟双删
 *
 * <pre>
 * 读路径（商品详情）：
 *   ① 缓存命中 ────────→ 直接返回
 *   ② 空值标记命中 ────→ 2001（防穿透第二层：DB 不存在/下架结果也被缓存）
 *   ③ 布隆过滤器拦截 ──→ 2001（防穿透第一层：确定不存在，不落缓存；未预热时自动降级放行）
 *   ④ 互斥锁重建 ──────→ Redisson tryLock + 双检 + 查库落缓存（防击穿：
 *      并发击穿同一冷 key 时只有持锁线程查库，其余线程降级直查 DB 不写缓存）
 *   ⑤ TTL 随机抖动 ────→ 基础 TTL + 随机偏移（防雪崩：同批写入的 key 不再同时过期）
 *
 * 写路径（一致性，供商品更新/上下架调用）：
 *   更新 DB → 删除缓存 → 延迟 double-delete-delay-millis 后再删一次（延迟双删：
 *   防止"读请求把更新前的旧值回填缓存"的窗口期脏数据）
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheService {

    /** 布隆过滤器容量与误判率（误判仅导致多查一次库，由空值缓存兜底） */
    private static final long BLOOM_EXPECTED_INSERTIONS = 10_000L;
    private static final double BLOOM_FALSE_PROBABILITY = 0.01;

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final ProductService productService;
    private final CacheEvictWorker cacheEvictWorker;
    private final ProductCacheProperties properties;

    // ==================== 商品详情 ====================

    /**
     * 商品详情（带缓存）：不存在或已下架抛 2001
     */
    public SpuDetailVO getSpuDetail(Long spuId) {
        RBucket<String> bucket = redissonClient.getBucket(CacheKeys.SPU_DETAIL_PREFIX + spuId);

        // ① 读缓存（命中 / 空值标记 / 损坏降级）
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

    /**
     * 数据变更后的缓存失效：删除 + （可选）延迟再删一次
     * <p>
     * 调用时机：商品更新/上下架等写路径「更新 DB 之后」（P3 管理后台将接入）。
     */
    public void evictSpuDetail(Long spuId, boolean doubleDelete) {
        String key = CacheKeys.SPU_DETAIL_PREFIX + spuId;
        redissonClient.getKeys().delete(key);
        log.info("[缓存] 已删除：key={}", key);
        if (doubleDelete) {
            cacheEvictWorker.scheduleEvict(key, properties.getDoubleDeleteDelayMillis());
        }
    }

    /**
     * 查看某商品的缓存状态（内部运维/验收接口用）
     */
    public CacheInspectVO inspectSpuDetail(Long spuId) {
        String key = CacheKeys.SPU_DETAIL_PREFIX + spuId;
        RBucket<String> bucket = redissonClient.getBucket(key);
        CacheInspectVO vo = new CacheInspectVO();
        vo.setKey(key);
        vo.setExists(bucket.isExists());
        vo.setTtlMillis(bucket.remainTimeToLive());
        String raw = bucket.get();
        if (raw != null) {
            vo.setNullMarker(CacheKeys.NULL_MARKER.equals(raw));
            vo.setContentLength(raw.length());
            vo.setPreview(raw.length() > 200 ? raw.substring(0, 200) + "..." : raw);
        }
        return vo;
    }

    // ==================== 分类列表 ====================

    /**
     * 分类列表（带缓存）
     * <p>
     * 取舍：不做互斥锁重建——分类低频变更、重建只是一条短查询，并发重建无害；
     * 失效走与详情相同的「删除 + 延迟双删」通道。
     */
    public List<CategoryVO> getCategories() {
        RBucket<String> bucket = redissonClient.getBucket(CacheKeys.CATEGORY_LIST);
        String cached = bucket.get();
        if (cached != null) {
            log.debug("[缓存] 分类列表命中");
            try {
                return objectMapper.readValue(cached, new TypeReference<List<CategoryVO>>() {
                });
            } catch (JsonProcessingException e) {
                log.warn("[缓存] 分类列表反序列化失败，删除损坏缓存并降级查库");
                bucket.delete();
            }
        }
        List<CategoryVO> list = productService.listCategories();
        long ttl = properties.getCategoryTtlSeconds() + randomJitter(properties.getSpuDetailJitterSeconds());
        bucket.set(toJson(list), Duration.ofSeconds(ttl));
        return list;
    }

    /** 分类数据变更后的缓存失效（供 P3 分类管理调用） */
    public void evictCategories(boolean doubleDelete) {
        redissonClient.getKeys().delete(CacheKeys.CATEGORY_LIST);
        log.info("[缓存] 已删除：key={}", CacheKeys.CATEGORY_LIST);
        if (doubleDelete) {
            cacheEvictWorker.scheduleEvict(CacheKeys.CATEGORY_LIST, properties.getDoubleDeleteDelayMillis());
        }
    }

    /** 新增商品上架时补入布隆过滤器（否则新 spuId 会被布隆误判为不存在） */
    public void addSpuToBloom(Long spuId) {
        bloomFilter().add(spuId);
        log.info("[缓存] 布隆过滤器已补入：spuId={}", spuId);
    }

    // ==================== 预热 ====================

    /**
     * 缓存预热：布隆过滤器重建 + 商品详情批量落缓存
     * <p>
     * 由启动 Runner 或内部接口触发；预热失败不影响服务启动（读路径全程可回源 DB）。
     */
    public String preheat() {
        List<Long> spuIds = productService.listOnlineSpuIds();

        RBloomFilter<Long> bloom = bloomFilter();
        for (Long spuId : spuIds) {
            bloom.add(spuId);
        }

        int written = 0;
        for (Long spuId : spuIds) {
            RBucket<String> bucket = redissonClient.getBucket(CacheKeys.SPU_DETAIL_PREFIX + spuId);
            if (bucket.isExists()) {
                continue;
            }
            writeSpuDetailCache(spuId, productService.loadSpuDetail(spuId));
            written++;
        }

        String summary = "预热完成：布隆过滤器 " + spuIds.size() + " 个 spuId，详情缓存写入 " + written + " 条";
        log.info("[缓存] {}", summary);
        return summary;
    }

    // ==================== 私有 ====================

    /**
     * 读详情缓存。三态：命中返回 VO；空值标记抛 2001；未命中或损坏返回 null（走重建）
     */
    private SpuDetailVO readSpuDetailCache(RBucket<String> bucket, Long spuId) {
        String cached = bucket.get();
        if (cached == null) {
            return null;
        }
        if (CacheKeys.NULL_MARKER.equals(cached)) {
            log.debug("[缓存] 空值标记命中（防穿透）：spuId={}", spuId);
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        try {
            log.debug("[缓存] 命中：spuId={}", spuId);
            return objectMapper.readValue(cached, SpuDetailVO.class);
        } catch (JsonProcessingException e) {
            // 缓存损坏自愈：删除脏 key 并降级查库
            log.warn("[缓存] 反序列化失败，删除损坏缓存并降级查库：spuId={}", spuId);
            bucket.delete();
            return null;
        }
    }

    /**
     * 写详情缓存。TTL = 基础值 + 随机抖动（防雪崩）；vo 为 null 时写空值标记（防穿透）
     */
    private void writeSpuDetailCache(Long spuId, SpuDetailVO vo) {
        RBucket<String> bucket = redissonClient.getBucket(CacheKeys.SPU_DETAIL_PREFIX + spuId);
        if (vo == null) {
            long ttl = properties.getNullValueTtlSeconds() + randomJitter(60);
            bucket.set(CacheKeys.NULL_MARKER, Duration.ofSeconds(ttl));
            log.debug("[缓存] 空值已缓存：spuId={}，ttl={}s", spuId, ttl);
            return;
        }
        long ttl = properties.getSpuDetailTtlSeconds() + randomJitter(properties.getSpuDetailJitterSeconds());
        bucket.set(toJson(vo), Duration.ofSeconds(ttl));
        log.debug("[缓存] 已写入：spuId={}，ttl={}s", spuId, ttl);
    }

    /**
     * 布隆过滤器判定。未预热（元素数为 0）时不拦截，避免误杀全部查询
     */
    private boolean mightExist(Long spuId) {
        RBloomFilter<Long> bloom = bloomFilter();
        if (bloom.count() == 0) {
            return true;
        }
        return bloom.contains(spuId);
    }

    private RBloomFilter<Long> bloomFilter() {
        RBloomFilter<Long> bloom = redissonClient.getBloomFilter(CacheKeys.SPU_BLOOM);
        // tryInit 幂等：已初始化时返回 false，不抛异常
        bloom.tryInit(BLOOM_EXPECTED_INSERTIONS, BLOOM_FALSE_PROBABILITY);
        return bloom;
    }

    private boolean tryLock(RLock lock) {
        try {
            return lock.tryLock(properties.getLockWaitMillis(), properties.getLockLeaseMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private SpuDetailVO requireFound(SpuDetailVO vo, Long spuId) {
        if (vo == null) {
            log.debug("[缓存] DB 未命中：spuId={}", spuId);
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        return vo;
    }

    private long randomJitter(long jitterSeconds) {
        return jitterSeconds <= 0 ? 0 : ThreadLocalRandom.current().nextLong(jitterSeconds + 1);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("缓存序列化失败", e);
        }
    }
}
