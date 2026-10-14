package com.eshop.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单表 t_payment（一笔有效支付对应一笔支付单，1:1 订单）
 */
@Data
@TableName("t_payment")
public class Payment {

    /** 状态：待支付 */
    public static final int STATUS_PENDING = 0;

    /** 状态：支付成功 */
    public static final int STATUS_SUCCESS = 1;

    /** 状态：支付失败 */
    public static final int STATUS_FAILED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentNo;

    private String orderNo;

    private Long userId;

    private BigDecimal amount;

    /** 支付方式：1 模拟支付 */
    private Integer payType;

    /** 状态：0 待支付 / 1 支付成功 / 2 支付失败 */
    private Integer status;

    private LocalDateTime payTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
