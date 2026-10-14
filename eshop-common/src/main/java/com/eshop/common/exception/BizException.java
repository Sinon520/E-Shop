package com.eshop.common.exception;

import com.eshop.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常：业务规则不满足时抛出，由全局异常处理器统一转换为响应
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = ResultCode.FAILED.getCode();
    }

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
