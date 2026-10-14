package com.eshop.common.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SKU 精简信息（跨服务通用）
 * <p>
 * 传输方向：eshop-product 内部接口（POST /internal/sku/batch）→
 * eshop-cart（购物车展示）与 eshop-order（下单快照）经 OpenFeign 消费
 */
@Data
public class SkuInfoDTO implements Serializable {

    /** SKU ID */
    private Long skuId;

    /** 所属 SPU ID */
    private Long spuId;

    /** 商品名称（SPU 名） */
    private String spuName;

    /** 规格文案（SKU 名，如：经典黑 / 标准版） */
    private String skuName;

    /** 展示图：优先 SKU 图，缺省回退 SPU 主图；可为空串 */
    private String image;

    /** 当前售价（实时取自商品服务） */
    private BigDecimal price;
}
