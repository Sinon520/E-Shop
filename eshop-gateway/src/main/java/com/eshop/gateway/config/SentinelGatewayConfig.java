package com.eshop.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.Set;

/**
 * Sentinel 网关限流（P2）：下单接口 QPS 限流 + 统一 429 响应
 * <p>
 * 规则以代码方式注入（版本化、可复现）；Dashboard 上可实时查看/调整。
 * 阈值可配置（eshop.sentinel.order-create-qps）：演示档 3；压测放开可加启动参数
 * {@code --eshop.sentinel.order-create-qps=100000}。
 * 生产建议：规则迁移到 Nacos 配置中心（sentinel-datasource-nacos）动态下发。
 */
@Slf4j
@Configuration
public class SentinelGatewayConfig {

    /** 自定义 API 分组名：下单接口 */
    private static final String API_ORDER_CREATE = "order-create";

    /** 下单接口限流阈值（QPS）：默认 3（演示值）；压测时通过启动参数放开 */
    @Value("${eshop.sentinel.order-create-qps:3}")
    private double orderCreateQps;

    @PostConstruct
    public void init() {
        initApiDefinitions();
        initGatewayRules();
        initBlockHandler();
        log.info("[Sentinel] 网关限流已装配：{} QPS ≤ {}", API_ORDER_CREATE, orderCreateQps);
    }

    /** 自定义 API 分组：精确匹配 POST /api/order/create（不打扰其他接口） */
    private void initApiDefinitions() {
        ApiDefinition orderCreate = new ApiDefinition(API_ORDER_CREATE)
                .setPredicateItems(Set.of(new ApiPathPredicateItem()
                        .setPattern("/api/order/create")
                        .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT)));
        GatewayApiDefinitionManager.loadApiDefinitions(Set.of(orderCreate));
    }

    /** 流控规则：order-create 分组 QPS ≤ orderCreateQps（快速失败，超限直接 429） */
    private void initGatewayRules() {
        GatewayFlowRule rule = new GatewayFlowRule(API_ORDER_CREATE)
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(orderCreateQps)
                .setIntervalSec(1);
        GatewayRuleManager.loadRules(Set.of(rule));
    }

    /** 限流响应：与全站统一响应同构的 JSON（HTTP 429） */
    private void initBlockHandler() {
        BlockRequestHandler handler = (exchange, throwable) -> ServerResponse
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\":429,\"message\":\"请求过于频繁，请稍后重试\",\"data\":null}");
        GatewayCallbackManager.setBlockHandler(handler);
    }
}
