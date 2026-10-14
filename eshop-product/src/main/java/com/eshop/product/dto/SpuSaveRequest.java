package com.eshop.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端商品保存请求（新增 / 编辑）
 */
@Data
public class SpuSaveRequest {

    @NotBlank(message = "商品名称不能为空")
    private String name;

    @NotNull(message = "请选择分类")
    private Long categoryId;

    /** 品牌 ID（0 或空 = 无品牌） */
    private Long brandId;

    private String mainImage;

    /** 富文本详情 */
    private String detail;

    /** 新增时附带的初始 SKU 列表（编辑场景暂不处理 SKU 修改） */
    private List<SkuItem> skus;

    @Data
    public static class SkuItem {

        @NotBlank(message = "SKU 名称不能为空")
        private String name;

        @NotNull(message = "SKU 售价不能为空")
        private BigDecimal price;

        private BigDecimal marketPrice;

        private String image;
    }
}
