package com.eshop.order.client;

import com.eshop.common.dto.StockOpRequest;
import com.eshop.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 库存服务内部接口客户端（锁定 / 确认 / 回补，全部按订单号幂等）
 * <p>
 * 熔断降级：feign.sentinel.enabled=true + fallbackFactory；
 * 资源名（Sentinel 约定）= POST:http://eshop-stock/internal/stock/lock（等），
 * 熔断规则见 {@link com.eshop.order.config.SentinelDegradeConfig}。
 */
@FeignClient(name = "eshop-stock", path = "/internal/stock", contextId = "orderStockClient",
        fallbackFactory = StockClientFallbackFactory.class)
public interface StockClient {

    /** 锁定库存（下单） */
    @PostMapping("/lock")
    Result<Void> lock(@RequestBody StockOpRequest request);

    /** 确认扣减（支付成功） */
    @PostMapping("/confirm")
    Result<Void> confirm(@RequestBody StockOpRequest request);

    /** 回补库存（取消 / 关单） */
    @PostMapping("/restore")
    Result<Void> restore(@RequestBody StockOpRequest request);
}
