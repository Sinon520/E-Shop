package com.eshop.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 商品详情内的 SKU 元素（GET /api/product/spu/{id}）
 */
@Data
public class SkuVO {

    private Long id;

    private String skuCode;

    private String name;

    /** 规格键值对，前端据此匹配所选规格组合 */
    private Map<String, String> specJson;

    private BigDecimal price;

    private BigDecimal marketPrice;

    private String image;

    /** 状态：0 下架 / 1 上架 */
    private Integer status;
}
