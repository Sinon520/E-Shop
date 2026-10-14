package com.eshop.order.mq;

import com.eshop.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 订单超时关单消费者：延迟消息到期后触发
 * <p>
 * - 消费失败（系统异常）时抛异常 → RocketMQ 自动重试，重试后仍会先查订单状态保证幂等；
 * - 业务上"无需处理"的情况（订单已支付/已取消）在 OrderService#handleTimeout 内静默跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = OrderMqConstants.TOPIC_ORDER_TIMEOUT,
        consumerGroup = OrderMqConstants.GROUP_ORDER_TIMEOUT_CONSUMER)
public class OrderTimeoutConsumer implements RocketMQListener<String> {

    private final OrderService orderService;

    @Override
    public void onMessage(String orderNo) {
        log.info("收到超时关单延迟消息: orderNo={}", orderNo);
        orderService.handleTimeout(orderNo);
    }
}
