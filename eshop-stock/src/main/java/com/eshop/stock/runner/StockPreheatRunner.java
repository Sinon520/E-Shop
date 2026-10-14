package com.eshop.stock.runner;

import com.eshop.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时预热 Redis 库存（防超卖第一层闸门就绪 + 以 DB 为准对账校准）
 * <p>
 * 异步执行、失败不阻断启动——Lua 层未预热时自动降级放行，交易由 DB 乐观锁兜底。
 */
@Slf4j
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
