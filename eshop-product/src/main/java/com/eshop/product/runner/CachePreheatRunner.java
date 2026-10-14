package com.eshop.product.runner;

import com.eshop.product.cache.ProductCacheService;
import com.eshop.product.config.ProductCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动缓存预热（防雪崩：服务启动后第一时间把热点数据铺进缓存，避免上线瞬间全量打库）
 * <p>
 * 异步执行、失败不阻断启动——读路径全程可回源 DB，预热只是加速手段。
 */
@Slf4j
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
        Thread thread = new Thread(() -> {
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
