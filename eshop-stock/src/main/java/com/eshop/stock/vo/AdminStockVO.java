package com.eshop.stock.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理端库存列表项（GET /api/admin/stock/page）
 */
@Data
public class AdminStockVO {

    private Long skuId;

    /** 展示名（product 服务补充）：SPU 名 + 规格 */
    private String spuName;

    private String skuName;

    private BigDecimal price;

    private Integer available;

    private Integer locked;

    private Integer sold;

    /** Redis 预扣值（null = 未预热） */
    private Long redisValue;

    /** Redis 与 DB 是否一致 */
    private Boolean consistent;
}
