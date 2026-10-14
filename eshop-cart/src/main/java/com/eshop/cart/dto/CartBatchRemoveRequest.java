package com.eshop.cart.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量删除请求（POST /api/cart/batch-remove）
 */
@Data
public class CartBatchRemoveRequest {

    @NotEmpty(message = "skuIds 不能为空")
    private List<Long> skuIds;
}
