package com.eshop.product.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理端商品列表项（GET /api/admin/product/spu/page）
 */
@Data
public class AdminSpuVO {

    private Long id;

    private String name;

    private String mainImage;

    private Long categoryId;

    /** 服务层回填：分类名 */
    private String categoryName;

    /** 该 SPU 下最低价 SKU 的售价（可能为空：无 SKU） */
    private BigDecimal price;

    private BigDecimal marketPrice;

    private Integer sales;

    /** 状态：0 下架 / 1 上架 */
    private Integer status;

    /** 服务层回填：SKU 数量 */
    private Integer skuCount;
}
