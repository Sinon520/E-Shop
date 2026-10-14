package com.eshop.cart.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 勾选 / 取消勾选请求（PUT /api/cart/checked）
 */
@Data
public class CartCheckedRequest {

    @NotNull(message = "skuId 不能为空")
    private Long skuId;

    @NotNull(message = "checked 不能为空")
    private Boolean checked;
}
