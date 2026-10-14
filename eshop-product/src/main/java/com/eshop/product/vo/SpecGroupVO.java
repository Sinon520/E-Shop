package com.eshop.product.vo;

import lombok.Data;

import java.util.List;

/**
 * 规格组（由该 SPU 全部 SKU 的 specJson 聚合而来，前端直接渲染规格选择器）
 */
@Data
public class SpecGroupVO {

    /** 规格名，如：颜色 */
    private String name;

    /** 规格值列表，如：["经典黑","云雾白","晨雾蓝"] */
    private List<String> values;
}
