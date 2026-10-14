package com.eshop.stock.vo;

import lombok.Data;

/**
 * SKU 可用库存（GET /api/stock/{skuId} 响应 data）
 */
@Data
public class StockVO {

    private Long skuId;

    /** 可售库存 */
    private Integer available;
}
