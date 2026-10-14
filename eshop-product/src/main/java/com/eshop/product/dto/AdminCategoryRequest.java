package com.eshop.product.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理端分类保存请求（新增 / 编辑）
 */
@Data
public class AdminCategoryRequest {

    @NotBlank(message = "分类名称不能为空")
    private String name;

    /** 父级 ID（0 = 一级分类） */
    private Long parentId;

    /** 层级：1 一级 / 2 二级（为空时按 parentId 推导） */
    private Integer level;

    /** 排序值（越小越靠前） */
    private Integer sort;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    private String icon;
}
