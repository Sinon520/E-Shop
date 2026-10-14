package com.eshop.order.mq;

/**
 * 订单域消息常量（P2）
 */
public final class OrderMqConstants {

    /** 订单超时关单延迟消息主题 */
    public static final String TOPIC_ORDER_TIMEOUT = "eshop-order-timeout";

    /** 超时关单消费者组 */
    public static final String GROUP_ORDER_TIMEOUT_CONSUMER = "eshop-order-timeout-consumer";

    private OrderMqConstants() {
    }
}
