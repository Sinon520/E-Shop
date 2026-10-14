package com.eshop.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单列表元素（GET /api/order/page）
 */
@Data
public class OrderVO {

    private String orderNo;

    /** 状态：0 待支付 / 1 已支付 / 2 已发货 / 3 已完成 / 4 已取消 / 5 超时关闭 */
    private Integer status;

    /** 状态文案（前端直接展示） */
    private String statusText;

    private BigDecimal totalAmount;

    private BigDecimal payAmount;

    /** yyyy-MM-dd HH:mm:ss */
    private String createTime;

    /** 收货人姓名（快照；管理端列表展示） */
    private String receiverName;

    private List<OrderItemVO> items;
}
