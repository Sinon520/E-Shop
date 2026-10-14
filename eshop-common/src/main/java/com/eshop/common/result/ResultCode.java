package com.eshop.common.result;

import lombok.Getter;

/**
 * 响应码枚举（见 docs/api-contract.md §1.3）
 * <p>
 * 200 段：HTTP 层通用码；1000 起：业务错误码，按域分段——
 * 1xxx 用户域 / 2xxx 商品域 / 3xxx 库存域 / 4xxx 购物车域
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),
    VALIDATE_FAILED(400, "参数校验失败"),
    UNAUTHORIZED(401, "暂未登录或登录已过期"),
    FORBIDDEN(403, "没有相关权限"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方式不支持"),

    // ==================== 用户域 1xxx ====================
    USERNAME_EXISTS(1001, "用户名已存在"),
    USERNAME_OR_PASSWORD_ERROR(1002, "用户名或密码错误"),
    ACCOUNT_DISABLED(1003, "账号已被禁用"),
    ADDRESS_NOT_FOUND(1004, "收货地址不存在"),

    // ==================== 商品域 2xxx ====================
    PRODUCT_NOT_FOUND(2001, "商品不存在或已下架"),

    // ==================== 库存域 3xxx ====================
    SKU_NOT_FOUND(3001, "SKU 不存在或已下架"),
    STOCK_INSUFFICIENT(3002, "库存不足"),

    // ==================== 购物车域 4xxx ====================
    CART_ITEM_NOT_FOUND(4001, "购物车条目不存在"),

    // ==================== 订单域 5xxx ====================
    ORDER_NOT_FOUND(5001, "订单不存在"),
    ORDER_STATUS_ILLEGAL(5002, "订单状态不允许该操作"),

    // ==================== 支付域 6xxx ====================
    PAYMENT_NOT_FOUND(6001, "支付单不存在");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
