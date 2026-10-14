package com.eshop.pay.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建支付单请求（POST /api/pay/create）
 */
@Data
public class PayCreateRequest {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;
}
