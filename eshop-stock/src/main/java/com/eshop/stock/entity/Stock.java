package com.eshop.stock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存表 t_stock（每 SKU 一行；乐观锁字段 version 供 P2 扣减使用）
 */
@Data
@TableName("t_stock")
public class Stock {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long skuId;

    /** 可售库存 */
    private Integer available;

    /** 锁定库存（已下单未支付确认） */
    private Integer locked;

    /** 已售数量 */
    private Integer sold;

    /** 乐观锁版本号 */
    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
