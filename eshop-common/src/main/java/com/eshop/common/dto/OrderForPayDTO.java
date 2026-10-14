package com.eshop.common.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单-支付协同信息
 * <p>
 * 传输方向：eshop-order 内部接口 → eshop-pay（创建支付单前校验订单与金额）
 */
@Data
public class OrderForPayDTO implements Serializable {

    private String orderNo;

    /** 订单归属用户 ID（支付侧校验归属） */
    private Long userId;

    /** 订单状态（0 待支付才可支付） */
    private Integer status;

    /** 应付金额 */
    private BigDecimal payAmount;
}
