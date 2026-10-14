package com.eshop.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单详情（GET /api/order/{orderNo}）
 */
@Data
public class OrderDetailVO {

    private String orderNo;

    private Integer status;

    private String statusText;

    private BigDecimal totalAmount;

    private BigDecimal payAmount;

    private String remark;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    /** yyyy-MM-dd HH:mm:ss */
    private String createTime;

    /** yyyy-MM-dd HH:mm:ss；未支付为 null */
    private String payTime;

    private List<OrderItemVO> items;
}
