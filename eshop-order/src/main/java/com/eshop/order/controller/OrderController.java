package com.eshop.order.controller;

import com.eshop.common.constant.AuthConstants;
import com.eshop.common.result.PageResult;
import com.eshop.common.result.Result;
import com.eshop.order.dto.OrderCreateRequest;
import com.eshop.order.service.OrderService;
import com.eshop.order.vo.OrderCreateVO;
import com.eshop.order.vo.OrderDetailVO;
import com.eshop.order.vo.OrderVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口（需登录；用户 ID 由网关校验 JWT 后经 X-User-Id 透传）
 */
@Tag(name = "订单", description = "下单 / 列表 / 详情 / 取消（需登录）")
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 创建订单（items 来自购物车勾选项或详情页立即购买） */
    @PostMapping("/create")
    public Result<OrderCreateVO> create(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                        @Valid @RequestBody OrderCreateRequest request) {
        return Result.success(orderService.create(userId, request));
    }

    /** 订单分页（可按状态过滤） */
    @GetMapping("/page")
    public Result<PageResult<OrderVO>> page(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                            @RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) Integer status) {
        return Result.success(orderService.page(userId, page, size, status));
    }

    /** 订单详情 */
    @GetMapping("/{orderNo:[A-Za-z0-9]+}")
    public Result<OrderDetailVO> detail(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                        @PathVariable String orderNo) {
        return Result.success(orderService.detail(userId, orderNo));
    }

    /** 取消订单（仅待支付） */
    @PutMapping("/{orderNo:[A-Za-z0-9]+}/cancel")
    public Result<Void> cancel(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                               @PathVariable String orderNo) {
        orderService.cancel(userId, orderNo);
        return Result.success();
    }
}
