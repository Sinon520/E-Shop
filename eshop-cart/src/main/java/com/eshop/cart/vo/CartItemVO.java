package com.eshop.cart.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购物车条目（GET /api/cart/list 响应 data 元素）
 */
@Data
public class CartItemVO {

    private Long skuId;

    private Long spuId;

    /** 商品名称（SPU 名） */
    private String name;

    /** 规格文案（如：经典黑 / 标准版） */
    private String specText;

    private String image;

    /** 实时价格（不存快照，成交价以下单时为准） */
    private BigDecimal price;

    private Integer quantity;

    private Boolean checked;
}
