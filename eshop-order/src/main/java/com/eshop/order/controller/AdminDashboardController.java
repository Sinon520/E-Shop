package com.eshop.order.controller;

import com.eshop.common.result.Result;
import com.eshop.order.service.DashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端数据看板接口（管理后台，P3；网关校验 role=admin）
 */
@Tag(name = "管理端-看板", description = "经营数据总览")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    /** 看板总览：今日成交 / 待发货 / 状态分布 / 7 日趋势 / 热销榜 */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.success(dashboardService.overview());
    }
}
