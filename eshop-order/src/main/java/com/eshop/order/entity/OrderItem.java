package com.eshop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细表 t_order_item（商品信息全部为下单时快照，不依赖商品服务）
 */
@Data
@TableName("t_order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long spuId;

    private Long skuId;

    /** 商品名称（快照） */
    private String spuName;

    /** 规格名称（快照） */
    private String skuName;

    /** 商品图（快照） */
    private String skuImage;

    /** 成交单价（快照） */
    private BigDecimal price;

    private Integer quantity;

    /** 小计金额 */
    private BigDecimal totalAmount;

    private LocalDateTime createTime;
}
