package com.eshop.product.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 商品缓存参数（application.yml -> eshop.cache）
 */
@Data
@Component
@ConfigurationProperties(prefix = "eshop.cache")
public class ProductCacheProperties {

    /** 商品详情基础 TTL（秒） */
    private long spuDetailTtlSeconds = 1800;

    /** TTL 随机抖动上限（秒）：实际 TTL = 基础值 + [0, 抖动]（防雪崩） */
    private long spuDetailJitterSeconds = 300;

    /** 分类列表基础 TTL（秒） */
    private long categoryTtlSeconds = 3600;

    /** 空值缓存 TTL（秒，防穿透） */
    private long nullValueTtlSeconds = 120;

    /** 互斥锁重建：tryLock 等待上限（毫秒） */
    private long lockWaitMillis = 500;

    /** 互斥锁重建：锁租约（毫秒，防死锁） */
    private long lockLeaseMillis = 10000;

    /** 延迟双删：第二次删除的延迟（毫秒） */
    private long doubleDeleteDelayMillis = 500;

    /** 启动预热开关 */
    private boolean preheatEnabled = true;
}
