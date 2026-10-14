package com.eshop.common.constant;

/**
 * 订单状态机常量（跨服务契约：订单服务维护状态，支付/后台等按编码判断；与 t_order.status 注释一致）
 * <p>
 * 0 待支付 → 1 已支付 → 2 已发货 → 3 已完成；4 已取消 / 5 超时关闭（P2 RocketMQ 延迟消息落地）
 */
public final class OrderStatus {

    private OrderStatus() {
    }

    public static final int PENDING_PAY = 0;

    public static final int PAID = 1;

    public static final int SHIPPED = 2;

    public static final int FINISHED = 3;

    public static final int CANCELLED = 4;

    public static final int CLOSED = 5;

    /** 日志用：初始状态（无前序状态） */
    public static final int INIT = -1;

    /** 状态文案（前端直接展示） */
    public static String text(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case PENDING_PAY -> "待支付";
            case PAID -> "已支付";
            case SHIPPED -> "已发货";
            case FINISHED -> "已完成";
            case CANCELLED -> "已取消";
            case CLOSED -> "已超时关闭";
            default -> "未知";
        };
    }

    /** 状态日志操作者：用户 / 管理员 / 系统 */
    public static final int OPERATOR_USER = 1;

    public static final int OPERATOR_ADMIN = 2;

    public static final int OPERATOR_SYSTEM = 3;
}
