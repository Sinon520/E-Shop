package com.eshop.stock.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 库存 Redis 预扣闸门（防超卖第一层：Lua 原子预扣，抗并发入口）
 *
 * <pre>
 * 定位：Redis 是「准入闸门」而非账本——热点并发在 Lua 层原子拦截/放行，
 *       DB 乐观锁（第二层）才是最终账本，绝对保证不超卖。
 *
 * 一致性策略（详见 docs P2 亮点文档）：
 *   - 正常链路：预扣 / 回补与 DB 同步增减，两边保持一致；
 *   - 降级路径一（键不存在/Redis 异常）：放行给 DB 兜底（可用性优先，方向安全）；
 *   - 降级路径二（Seata 全局回滚不经过本服务）：Redis 值与 DB 短暂漂移（偏严格，少卖不超卖），
 *     由「预热 = 以 DB 为准全量重建」承担对账校准（每次重启自动收敛，生产可挂定时任务）。
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockRedisGate {

    /** Redis key 前缀：stock:sku:{skuId}，值 = 可售余量 */
    public static final String KEY_PREFIX = "stock:sku:";

    /** 预扣结果三态 */
    public enum DeductResult {
        /** 预扣成功（Redis 已扣减，需在失败路径回滚） */
        SUCCESS,
        /** 余量不足（直接拒绝，不打 DB） */
        INSUFFICIENT,
        /** 键不存在 / Redis 异常 → 降级放行（DB 乐观锁兜底） */
        BYPASS
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> stockDeductScript;
    private final DefaultRedisScript<Long> stockRestoreScript;

    /**
     * Lua 原子预扣：判断与扣减在 Redis 单线程内完成，天然无并发竞态
     */
    public DeductResult deduct(Long skuId, int quantity) {
        try {
            Long result = stringRedisTemplate.execute(stockDeductScript,
                    List.of(key(skuId)), String.valueOf(quantity));
            if (result == null || result == -1L) {
                return DeductResult.BYPASS;
            }
            if (result == -2L) {
                log.info("[库存] Redis 预扣拒绝（余量不足）: skuId={}, quantity={}", skuId, quantity);
                return DeductResult.INSUFFICIENT;
            }
            log.debug("[库存] Redis 预扣成功: skuId={}, quantity={}, remain={}", skuId, quantity, result);
            return DeductResult.SUCCESS;
        } catch (Exception e) {
            // Redis 是加速层而非依赖层：异常时降级放行，交易由 DB 兜底
            log.warn("[库存] Redis 预扣降级放行: skuId={}, err={}", skuId, e.getMessage());
            return DeductResult.BYPASS;
        }
    }

    /**
     * 加回（回滚预扣 / 取消回补共用）。best-effort，异常不阻断主流程；
     * 键不存在时跳过（不凭空创建，避免污染未预热 SKU）
     */
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
