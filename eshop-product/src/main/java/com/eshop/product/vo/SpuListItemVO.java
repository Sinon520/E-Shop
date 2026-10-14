package com.eshop.product.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品列表元素（GET /api/product/spu/page）
 * <p>
 * price = 该 SPU 下最低 SKU 售价；marketPrice = 对应 SKU 的划线价（后端聚合计算）
 */
@Data
public class SpuListItemVO {

    private Long id;

    private String name;

    private String mainImage;

    private BigDecimal price;

    private BigDecimal marketPrice;

    private Integer sales;

    private Long categoryId;

    /** 由服务层回填（分类名） */
    private String categoryName;
}
