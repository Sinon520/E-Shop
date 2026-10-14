package com.eshop.pay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eshop.common.constant.OrderStatus;
import com.eshop.common.dto.OrderForPayDTO;
import com.eshop.common.exception.BizException;
import com.eshop.common.result.ResultCode;
import com.eshop.common.util.ResultHelper;
import com.eshop.pay.client.OrderClient;
import com.eshop.pay.dto.PayCallbackRequest;
import com.eshop.pay.dto.PayCreateRequest;
import com.eshop.pay.entity.PayCallbackLog;
import com.eshop.pay.entity.Payment;
import com.eshop.pay.mapper.PayCallbackLogMapper;
import com.eshop.pay.mapper.PaymentMapper;
import com.eshop.pay.vo.PayCreateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付服务：创建支付单（1:1 订单）+ 模拟回调（幂等）
 * <p>
 * 幂等设计（回调侧三重防护）：
 * 1. t_pay_callback_log.uk_out_trade_no 挡重复回调（并发重复直接幂等返回）；
 * 2. 支付单状态机：仅 待支付 → 成功/失败，状态已终态则不再流转；
 * 3. 通知订单 mark-paid 幂等（订单侧 CAS 状态流转 + 库存侧流水去重）。
 * <p>
 * P2 演进：回调 → 订单/库存 的同步调用改为 MQ 最终一致（process_status=0 的日志即补偿切入点）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {

    private static final DateTimeFormatter PAY_NO_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentMapper paymentMapper;
    private final PayCallbackLogMapper payCallbackLogMapper;
    private final OrderClient orderClient;

    /**
     * 创建支付单：校验订单（存在 / 归属 / 待支付）；同一订单重开收银台幂等复用支付单
     */
    public PayCreateVO create(Long userId, PayCreateRequest request) {
        String orderNo = request.getOrderNo().trim();
        OrderForPayDTO order = fetchOrder(orderNo);
        if (!userId.equals(order.getUserId())) {
            // 不暴露他人订单是否存在
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() == null || order.getStatus() != OrderStatus.PENDING_PAY) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL);
        }

        Payment payment = findByOrderNo(orderNo);
        if (payment == null) {
            payment = new Payment();
            payment.setPaymentNo(generatePaymentNo());
            payment.setOrderNo(orderNo);
            payment.setUserId(userId);
            payment.setAmount(order.getPayAmount());
            payment.setPayType(1);
            payment.setStatus(Payment.STATUS_PENDING);
            try {
                paymentMapper.insert(payment);
            } catch (DuplicateKeyException e) {
                // 并发创建：以已存在的支付单为准（uk_payment_no 兜底）
                payment = findByOrderNo(orderNo);
                if (payment == null) {
                    throw new BizException("支付单创建失败，请重试");
                }
            }
        } else if (payment.getStatus() == Payment.STATUS_FAILED) {
            // 上次支付失败后重开收银台：重置为待支付
            payment.setStatus(Payment.STATUS_PENDING);
            paymentMapper.updateById(payment);
        }

        PayCreateVO vo = new PayCreateVO();
        vo.setPaymentNo(payment.getPaymentNo());
        vo.setOrderNo(payment.getOrderNo());
        vo.setAmount(payment.getAmount());
        vo.setStatus(payment.getStatus());
        return vo;
    }

    /**
     * 模拟支付回调：幂等（见类注释）；成功后同步通知订单服务标记已支付
     */
    public void callback(PayCallbackRequest request) {
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getPaymentNo, request.getPaymentNo()));
        if (payment == null) {
            throw new BizException(ResultCode.PAYMENT_NOT_FOUND);
        }
        String outTradeNo = request.getOutTradeNo().trim();

        // 幂等日志：已处理完成 → 直接返回
        PayCallbackLog existingLog = findLog(outTradeNo);
        if (existingLog != null && existingLog.getProcessStatus() != null
                && existingLog.getProcessStatus() == 1) {
            log.info("重复回调（已处理），幂等返回: outTradeNo={}", outTradeNo);
            return;
        }
        if (existingLog == null) {
            PayCallbackLog callbackLog = new PayCallbackLog();
            callbackLog.setPaymentNo(payment.getPaymentNo());
            callbackLog.setOutTradeNo(outTradeNo);
            callbackLog.setRawContent(buildRawContent(request));
            callbackLog.setProcessStatus(0);
            try {
                payCallbackLogMapper.insert(callbackLog);
            } catch (DuplicateKeyException e) {
                // 并发重复回调：另一路在处理，幂等返回
                log.info("并发重复回调，幂等返回: outTradeNo={}", outTradeNo);
                return;
            }
        }

        // 处理（下游全链路幂等，重复但未完成的回调可安全重放）
        if (Boolean.TRUE.equals(request.getSuccess())) {
            if (payment.getStatus() != Payment.STATUS_SUCCESS) {
                payment.setStatus(Payment.STATUS_SUCCESS);
                payment.setPayTime(LocalDateTime.now());
                paymentMapper.updateById(payment);
            }
            if (notifyOrderPaid(payment.getOrderNo())) {
                markLogProcessed(outTradeNo);
            }
        } else {
            if (payment.getStatus() == Payment.STATUS_PENDING) {
                payment.setStatus(Payment.STATUS_FAILED);
                paymentMapper.updateById(payment);
            }
            markLogProcessed(outTradeNo);
        }
        log.info("支付回调处理完成: paymentNo={}, outTradeNo={}, success={}",
                payment.getPaymentNo(), outTradeNo, request.getSuccess());
    }

    // ==================== 内部工具 ====================

    private OrderForPayDTO fetchOrder(String orderNo) {
        try {
            return ResultHelper.unwrap(orderClient.getForPay(orderNo));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用订单服务失败", e);
            throw new BizException("订单服务暂不可用，请稍后重试");
        }
    }

    /** 通知订单已支付；失败返回 false（回调日志留 process_status=0 待补偿） */
    private boolean notifyOrderPaid(String orderNo) {
        try {
            ResultHelper.unwrap(orderClient.markPaid(orderNo));
            return true;
        } catch (Exception e) {
            log.error("支付成功通知订单失败，待补偿: orderNo={}", orderNo, e);
            return false;
        }
    }

    private Payment findByOrderNo(String orderNo) {
        return paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderNo, orderNo));
    }

    private PayCallbackLog findLog(String outTradeNo) {
        return payCallbackLogMapper.selectOne(new LambdaQueryWrapper<PayCallbackLog>()
                .eq(PayCallbackLog::getOutTradeNo, outTradeNo));
    }

    private void markLogProcessed(String outTradeNo) {
        PayCallbackLog update = new PayCallbackLog();
        update.setProcessStatus(1);
        payCallbackLogMapper.update(update, new LambdaUpdateWrapper<PayCallbackLog>()
                .eq(PayCallbackLog::getOutTradeNo, outTradeNo));
    }

    private String buildRawContent(PayCallbackRequest request) {
        return String.format("{\"paymentNo\":\"%s\",\"outTradeNo\":\"%s\",\"success\":%s}",
                request.getPaymentNo(), request.getOutTradeNo(), request.getSuccess());
    }

    private String generatePaymentNo() {
        // PAY + 14 位时间 + 6 位随机，共 23 位（< 32，DB 唯一索引兜底）
        return "PAY" + PAY_NO_TIME.format(LocalDateTime.now())
                + ThreadLocalRandom.current().nextInt(100000, 1000000);
    }
}
