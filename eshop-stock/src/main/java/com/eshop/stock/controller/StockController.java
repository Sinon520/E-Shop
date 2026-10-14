package com.eshop.stock.controller;

import com.eshop.common.result.Result;
import com.eshop.stock.service.StockService;
import com.eshop.stock.vo.StockVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存接口（GET /api/stock/{skuId}，免鉴权）
 */
@Tag(name = "库存", description = "SKU 可用库存查询")
@RestController
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    /** 查询 SKU 可用库存（路径约束为纯数字，避免把 /doc.html 等单段路径误匹配进来） */
    @GetMapping("/{skuId:\\d+}")
    public Result<StockVO> getStock(@PathVariable Long skuId) {
        return Result.success(stockService.getAvailable(skuId));
    }
}
