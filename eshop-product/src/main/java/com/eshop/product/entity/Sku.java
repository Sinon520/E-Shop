package com.eshop.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 商品 SKU 表 t_sku
 * <p>
 * spec_json 为 MySQL JSON 列，经 JacksonTypeHandler 映射为 Map&lt;String, String&gt;（如 {"颜色":"经典黑","版本":"标准版"}）。
 */
@Data
@TableName(value = "t_sku", autoResultMap = true)
public class Sku {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long spuId;

    /** SKU 编码（仓储/对账用） */
    private String skuCode;

    /** 规格名称，如：经典黑 / 标准版 */
    private String name;

    /** 规格键值对 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> specJson;

    /** 销售价 */
    private BigDecimal price;

    /** 划线价（市场价） */
    private BigDecimal marketPrice;

    private String image;

    /** 状态：0 下架 / 1 上架 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
