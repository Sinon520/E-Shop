package com.eshop.order.config;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Sentinel 熔断降级规则（P2）：对「订单 → 库存」的 Feign 调用熔断
 *
 * <pre>
 * 资源名 = SCA SentinelInvocationHandler 约定：HTTP方法:目标URL:路径
 *   POST:http://eshop-stock/internal/stock/lock  （锁库存，下单主链路）
 *
 * 熔断策略：异常比例——统计窗口内（10s）至少 5 次调用且异常比例 > 50% → 熔断 10s；
 * 熔断期间调用直接走 StockClientFallbackFactory（快速失败，不再发起远程请求），
 * 窗口后半开试探，下游恢复后自动闭合。
 * </pre>
 */
@Slf4j
@Configuration
public class SentinelDegradeConfig {

    /** 库存锁定 Feign 资源名（锁定是下单链路关键路径，先对其熔断） */
    private static final String RESOURCE_STOCK_LOCK = "POST:http://eshop-stock/internal/stock/lock";

    @PostConstruct
    public void initDegradeRules() {
        DegradeRule rule = new DegradeRule(RESOURCE_STOCK_LOCK)
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())  // 异常比例
                .setCount(0.5)              // 异常比例阈值：50%
                .setStatIntervalMs(10000)   // 统计时长：10s
                .setMinRequestAmount(5)     // 最小请求数（避免小样本误熔断）
                .setTimeWindow(10);         // 熔断时长：10s
        DegradeRuleManager.loadRules(List.of(rule));
        log.info("[Sentinel] 熔断规则已装配：{}（异常比例 > 50% / 10s → 熔断 10s）", RESOURCE_STOCK_LOCK);
    }
}
