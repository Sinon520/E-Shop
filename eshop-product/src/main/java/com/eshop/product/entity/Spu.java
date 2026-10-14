package com.eshop.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品 SPU 表 t_spu
 * <p>
 * images 为 MySQL JSON 列，经 JacksonTypeHandler 映射为 List&lt;String&gt;；
 * autoResultMap = true 使自定义 typeHandler 在查询结果映射中生效。
 */
@Data
@TableName(value = "t_spu", autoResultMap = true)
public class Spu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long categoryId;

    /** 品牌 ID（0 无品牌） */
    private Long brandId;

    private String mainImage;

    /** 轮播图 URL 数组 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    /** 富文本详情 HTML */
    private String detail;

    /** 状态：0 下架 / 1 上架 */
    private Integer status;

    /** 累计销量 */
    private Integer sales;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
