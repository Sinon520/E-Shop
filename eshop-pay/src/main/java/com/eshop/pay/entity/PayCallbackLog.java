package com.eshop.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付回调日志表 t_pay_callback_log（幂等核心：uk_out_trade_no 挡住重复回调）
 */
@Data
@TableName("t_pay_callback_log")
public class PayCallbackLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentNo;

    /** 第三方交易流水号（唯一，幂等去重键） */
    private String outTradeNo;

    /** 原始回调报文 */
    private String rawContent;

    /** 处理状态：0 未处理 / 1 已处理 */
    private Integer processStatus;

    private LocalDateTime createTime;
}
