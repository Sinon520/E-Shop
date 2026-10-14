package com.eshop.order.controller;

import com.eshop.common.result.PageResult;
import com.eshop.common.result.Result;
import com.eshop.order.service.OrderService;
import com.eshop.order.vo.OrderDetailVO;
import com.eshop.order.vo.OrderVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端订单接口（管理后台，P3；网关校验 role=admin）
 */
@Tag(name = "管理端-订单", description = "订单列表 / 详情 / 发货")
@RestController
@RequestMapping("/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    /** 订单分页（全量） */
    @GetMapping("/page")
    public Result<PageResult<OrderVO>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) String keyword) {
        return Result.success(orderService.adminPage(page, size, status, keyword));
    }

    /** 订单详情（不限归属） */
    @GetMapping("/{orderNo}")
    public Result<OrderDetailVO> detail(@PathVariable String orderNo) {
        return Result.success(orderService.adminDetail(orderNo));
    }

    /** 发货（已支付 → 已发货） */
    @PutMapping("/{orderNo}/ship")
    public Result<Void> ship(@PathVariable String orderNo,
                             @RequestHeader(value = "X-User-Id", required = false) Long adminId) {
        orderService.ship(orderNo, adminId);
        return Result.success();
    }
}
