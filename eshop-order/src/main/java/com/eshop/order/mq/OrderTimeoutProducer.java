package com.eshop.order.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 订单超时消息生产者：下单成功后发送延迟消息（RocketMQ 定时/延迟消息）
 * <p>
 * 延迟级别由 {@code eshop.mq.order-timeout-delay-level} 配置（默认 16 = 30 分钟）。
 * 消息体为订单号；消费端按订单号查最新状态，具备天然幂等。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutProducer {

    private static final long SEND_TIMEOUT_MILLIS = 3000;

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${eshop.mq.order-timeout-delay-level:16}")
    private int delayLevel;

    /**
     * 发送超时关单延迟消息（同步发送；失败由调用方决定处理策略）
     */
    public void send(String orderNo) {
        Message<String> message = MessageBuilder.withPayload(orderNo).build();
        rocketMQTemplate.syncSend(OrderMqConstants.TOPIC_ORDER_TIMEOUT, message,
                SEND_TIMEOUT_MILLIS, delayLevel);
        log.info("超时关单消息已发送: orderNo={}, delayLevel={}", orderNo, delayLevel);
    }
}
