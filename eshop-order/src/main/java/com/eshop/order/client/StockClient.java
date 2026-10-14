package com.eshop.order.client;

import com.eshop.common.dto.StockOpRequest;
import com.eshop.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 库存服务内部接口客户端（锁定 / 确认 / 回补，全部按订单号幂等）
 */
@FeignClient(name = "eshop-stock", path = "/internal/stock", contextId = "orderStockClient")
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
