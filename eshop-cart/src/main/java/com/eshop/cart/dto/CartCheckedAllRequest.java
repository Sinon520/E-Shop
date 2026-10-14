package com.eshop.cart.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 全选 / 取消全选请求（PUT /api/cart/checked-all）
 */
@Data
public class CartCheckedAllRequest {

    @NotNull(message = "checked 不能为空")
    private Boolean checked;
}
