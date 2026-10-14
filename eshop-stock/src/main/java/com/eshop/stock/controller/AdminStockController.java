package com.eshop.stock.controller;

import com.eshop.common.result.PageResult;
import com.eshop.common.result.Result;
import com.eshop.stock.dto.StockAdjustRequest;
import com.eshop.stock.service.AdminStockService;
import com.eshop.stock.vo.AdminStockVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端库存接口（管理后台，P3；网关校验 role=admin）
 */
@Tag(name = "管理端-库存", description = "库存总览 / 调整库存")
@RestController
@RequestMapping("/admin/stock")
@RequiredArgsConstructor
public class AdminStockController {

    private final AdminStockService adminStockService;

    /** 库存分页（含 Redis 预扣值核对） */
    @GetMapping("/page")
    public Result<PageResult<AdminStockVO>> page(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Boolean lowStock) {
        return Result.success(adminStockService.page(page, size, keyword, lowStock));
    }

    /** 调整库存（delta 增量 / absolute 绝对值） */
    @PutMapping("/{skuId}/adjust")
    public Result<Void> adjust(@PathVariable Long skuId, @RequestBody @Valid StockAdjustRequest request) {
        adminStockService.adjust(skuId, request);
        return Result.success();
    }
}
