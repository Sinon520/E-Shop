package com.eshop.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细元素（快照字段）
 */
@Data
public class OrderItemVO {

    private Long skuId;

    private Long spuId;

    /** 商品名称（快照） */
    private String name;

    /** 规格文案（快照） */
    private String specText;

    private String image;

    /** 成交单价（快照） */
    private BigDecimal price;

    private Integer quantity;

    /** 小计 */
    private BigDecimal totalAmount;
}
