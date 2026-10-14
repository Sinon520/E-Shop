package com.eshop.common.util;

import com.eshop.common.exception.BizException;
import com.eshop.common.result.Result;
import com.eshop.common.result.ResultCode;

/**
 * 远程调用结果解包工具（OpenFeign 消费方使用）
 * <p>
 * 统一响应体约定下，远程业务失败以 code 承载——解包时原样转抛 {@link BizException}，
 * 保证业务错误码（如 3002 库存不足）跨服务透传到最终调用方。
 */
public final class ResultHelper {

    private ResultHelper() {
    }

    /**
     * 解包：成功返回 data；失败抛出携带原始业务码的 BizException
     */
    public static <T> T unwrap(Result<T> result) {
        if (result == null) {
            throw new BizException("远程服务无响应，请稍后重试");
        }
        if (result.getCode() != ResultCode.SUCCESS.getCode()) {
            throw new BizException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }
}
