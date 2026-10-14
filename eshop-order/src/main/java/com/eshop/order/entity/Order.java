package com.eshop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表 t_order（收货信息为下单时快照，见 docs/er-design.md）
 */
@Data
@TableName("t_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号（业务唯一） */
    private String orderNo;

    private Long userId;

    /** 商品总额 */
    private BigDecimal totalAmount;

    /** 应付金额 */
    private BigDecimal payAmount;

    /** 状态：见 {@link com.eshop.common.constant.OrderStatus} */
    private Integer status;

    private String receiverName;

    private String receiverPhone;

    /** 收货地址（省市区 + 详细，快照） */
    private String receiverAddress;

    private String remark;

    private LocalDateTime payTime;

    private LocalDateTime shipTime;

    private LocalDateTime finishTime;

    private LocalDateTime closeTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
