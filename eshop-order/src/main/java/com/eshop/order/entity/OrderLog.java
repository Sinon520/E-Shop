package com.eshop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态流转日志 t_order_log
 */
@Data
@TableName("t_order_log")
public class OrderLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    /** 原状态（-1 表示初始创建） */
    private Integer fromStatus;

    /** 新状态 */
    private Integer toStatus;

    /** 操作者类型：1 用户 / 2 管理员 / 3 系统 */
    private Integer operatorType;

    private Long operatorId;

    private String remark;

    private LocalDateTime createTime;
}
