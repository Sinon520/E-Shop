package com.eshop.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 加入购物车请求（POST /api/cart/add）
 */
@Data
public class CartAddRequest {

    @NotNull(message = "skuId 不能为空")
    private Long skuId;

    /** 数量，默认 1，上限 99（超出按上限截断） */
    @Min(value = 1, message = "数量不能小于 1")
    @Max(value = 99, message = "数量不能超过 99")
    private Integer quantity;
}
