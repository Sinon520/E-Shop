package com.eshop.pay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 模拟支付回调请求（POST /api/pay/callback）
 * <p>
 * 模拟第三方支付平台回调：outTradeNo 为第三方流水号（幂等去重键），
 * 前端演示时传 `MOCK-{paymentNo}` 稳定值即可安全重复触发。
 */
@Data
public class PayCallbackRequest {

    @NotBlank(message = "支付单号不能为空")
    private String paymentNo;

    @NotBlank(message = "第三方流水号不能为空")
    private String outTradeNo;

    /** 支付结果：true 成功 / false 失败 */
    @NotNull(message = "支付结果不能为空")
    private Boolean success;
}
