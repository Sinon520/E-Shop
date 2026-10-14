package com.eshop.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改数量请求（PUT /api/cart/quantity）
 */
@Data
public class CartQuantityRequest {

    @NotNull(message = "skuId 不能为空")
    private Long skuId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量不能小于 1（减到 0 请走删除接口）")
    @Max(value = 99, message = "数量不能超过 99")
    private Integer quantity;
}
