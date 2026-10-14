package com.eshop.order.client;

import com.eshop.common.dto.StockOpRequest;
import com.eshop.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 库存服务 Feign 调用降级工厂（Sentinel 熔断 / 调用异常时触发）
 * <p>
 * 返回统一的「降级 Result」，由调用方 ResultHelper.unwrap 转为业务异常，
 * 使订单流程（含 Seata 全局事务）保持一致的失败语义：快速失败 + 全局回滚。
 */
@Slf4j
@Component
public class StockClientFallbackFactory implements FallbackFactory<StockClient> {

    private static final String DEGRADE_MESSAGE = "库存服务暂不可用（熔断降级），请稍后重试";

    @Override
    public StockClient create(Throwable cause) {
        log.warn("[Sentinel] 库存服务调用降级: {}", cause == null ? "unknown" : cause.getMessage());
        return new StockClient() {

            @Override
            public Result<Void> lock(StockOpRequest request) {
                return Result.failed(DEGRADE_MESSAGE);
            }

            @Override
            public Result<Void> confirm(StockOpRequest request) {
                return Result.failed(DEGRADE_MESSAGE);
            }

            @Override
            public Result<Void> restore(StockOpRequest request) {
                return Result.failed(DEGRADE_MESSAGE);
            }
        };
    }
}
