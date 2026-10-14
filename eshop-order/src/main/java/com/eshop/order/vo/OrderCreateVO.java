package com.eshop.order.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建订单响应（data）
 */
@Data
@AllArgsConstructor
public class OrderCreateVO {

    private String orderNo;

    /** 应付金额（= 商品总额，本期无运费） */
    private BigDecimal payAmount;
}
