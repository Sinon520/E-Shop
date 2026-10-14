package com.eshop.pay.client;

import com.eshop.common.dto.OrderForPayDTO;
import com.eshop.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 订单服务内部接口客户端（创建支付单前校验 / 支付回调标记已支付）
 */
@FeignClient(name = "eshop-order", path = "/internal/order", contextId = "payOrderClient")
public interface OrderClient {

    /** 取订单支付信息 */
    @GetMapping("/{orderNo}")
    Result<OrderForPayDTO> getForPay(@PathVariable("orderNo") String orderNo);

    /** 标记订单已支付（幂等） */
    @PostMapping("/{orderNo}/mark-paid")
    Result<Void> markPaid(@PathVariable("orderNo") String orderNo);
}
