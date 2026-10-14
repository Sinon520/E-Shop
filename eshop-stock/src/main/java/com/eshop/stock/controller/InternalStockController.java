package com.eshop.stock.controller;

import com.eshop.common.dto.StockOpRequest;
import com.eshop.common.result.Result;
import com.eshop.stock.service.StockService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存内部接口（服务间调用）
 * <p>
 * 路径统一 /internal/ 前缀（网关对该前缀一律 404，不经网关暴露）；
 * 供 eshop-order（锁定 / 回补）与 eshop-pay 链路（确认扣减）经 OpenFeign 调用。
 * 全部按 (order_no, sku_id, type) 幂等。
 */
@Tag(name = "库存（内部）", description = "锁定 / 确认 / 回补")
@RestController
@RequestMapping("/internal/stock")
@RequiredArgsConstructor
public class InternalStockController {

    private final StockService stockService;

    /** 锁定库存（下单） */
    @PostMapping("/lock")
    public Result<Void> lock(@RequestBody StockOpRequest request) {
        stockService.lock(request);
        return Result.success();
    }

    /** 确认扣减（支付成功） */
    @PostMapping("/confirm")
    public Result<Void> confirm(@RequestBody StockOpRequest request) {
        stockService.confirm(request);
        return Result.success();
    }

    /** 回补库存（取消 / 关单） */
    @PostMapping("/restore")
    public Result<Void> restore(@RequestBody StockOpRequest request) {
        stockService.restore(request);
        return Result.success();
    }
}
