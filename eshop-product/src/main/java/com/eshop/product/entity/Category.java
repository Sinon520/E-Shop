package com.eshop.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品分类表 t_category（两级）
 */
@Data
@TableName("t_category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父级 ID，0 为一级分类 */
    private Long parentId;

    private String name;

    /** 层级：1 一级 / 2 二级 */
    private Integer level;

    /** 排序值（越小越靠前） */
    private Integer sort;

    private String icon;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
