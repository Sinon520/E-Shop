package com.eshop.product.vo;

import lombok.Data;

/**
 * 分类列表元素（GET /api/product/categories）
 */
@Data
public class CategoryVO {

    private Long id;

    private Long parentId;

    private String name;

    private Integer level;

    private String icon;

    private Integer sort;
}
