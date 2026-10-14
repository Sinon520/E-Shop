package com.eshop.order.controller;

import com.eshop.common.dto.OrderForPayDTO;
import com.eshop.common.result.Result;
import com.eshop.order.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单内部接口（服务间调用）
 * <p>
 * 路径统一 /internal/ 前缀（网关对该前缀一律 404，不经网关暴露）；
 * 供 eshop-pay 创建支付单校验 / 支付回调标记已支付（幂等）。
 */
@Tag(name = "订单（内部）", description = "支付协同")
@RestController
@RequestMapping("/internal/order")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;

    /** 取订单支付信息（支付服务创建支付单前校验） */
    @GetMapping("/{orderNo}")
    public Result<OrderForPayDTO> getForPay(@PathVariable String orderNo) {
        return Result.success(orderService.getForPay(orderNo));
    }

    /** 标记订单已支付（支付回调触发；幂等） */
    @PostMapping("/{orderNo}/mark-paid")
    public Result<Void> markPaid(@PathVariable String orderNo) {
        orderService.markPaid(orderNo);
        return Result.success();
    }
}
