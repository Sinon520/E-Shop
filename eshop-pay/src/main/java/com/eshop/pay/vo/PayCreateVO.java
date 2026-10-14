package com.eshop.pay.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建支付单响应（data）
 */
@Data
public class PayCreateVO {

    private String paymentNo;

    private String orderNo;

    private BigDecimal amount;

    /** 状态：0 待支付 / 1 支付成功 / 2 支付失败 */
    private Integer status;
}
