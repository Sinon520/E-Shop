package com.eshop.order.controller;

import com.eshop.common.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 健康检查接口（P0 验收用，保留作为服务巡检）
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @Value("${server.port}")
    private String port;

    @Value("${spring.application.name}")
    private String serviceName;

    @GetMapping
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
                "service", serviceName,
                "port", port,
                "time", LocalDateTime.now().toString()
        ));
    }
}
