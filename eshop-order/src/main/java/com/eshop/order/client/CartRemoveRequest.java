package com.eshop.order.client;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 购物车批量删除请求（与 eshop-cart 的 /batch-remove 请求体字段对齐）
 */
@Data
@AllArgsConstructor
public class CartRemoveRequest {

    private List<Long> skuIds;
}
