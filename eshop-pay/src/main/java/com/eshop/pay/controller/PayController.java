package com.eshop.pay.controller;

import com.eshop.common.constant.AuthConstants;
import com.eshop.common.result.Result;
import com.eshop.pay.dto.PayCallbackRequest;
import com.eshop.pay.dto.PayCreateRequest;
import com.eshop.pay.service.PayService;
import com.eshop.pay.vo.PayCreateVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付接口（需登录）
 */
@Tag(name = "支付", description = "支付单 / 模拟回调（需登录）")
@RestController
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;

    /** 创建支付单（同一订单幂等复用） */
    @PostMapping("/create")
    public Result<PayCreateVO> create(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                      @Valid @RequestBody PayCreateRequest request) {
        return Result.success(payService.create(userId, request));
    }

    /**
     * 模拟支付回调
     * <p>
     * 真实场景由第三方支付平台服务器回调商户服务器（需验签）；
     * 本项目演示由前端「模拟收银台」触发，outTradeNo 传稳定值（如 MOCK-{paymentNo}）即可验证幂等。
     */
    @PostMapping("/callback")
    public Result<Void> callback(@Valid @RequestBody PayCallbackRequest request) {
        payService.callback(request);
        return Result.success();
    }
}
