package com.eshop.product.vo;

import lombok.Data;

import java.util.List;

/**
 * 商品详情（GET /api/product/spu/{id} 响应 data）
 */
@Data
public class SpuDetailVO {

    private Long id;

    private String name;

    private Long categoryId;

    private String categoryName;

    private String brandName;

    private String mainImage;

    /** 轮播图 URL 数组 */
    private List<String> images;

    /** 富文本详情 HTML */
    private String detail;

    /** 状态：0 下架 / 1 上架 */
    private Integer status;

    private Integer sales;

    /** 规格组（聚合） */
    private List<SpecGroupVO> specs;

    /** SKU 列表 */
    private List<SkuVO> skus;
}
