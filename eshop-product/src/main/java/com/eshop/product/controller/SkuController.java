package com.eshop.product.controller;

import com.eshop.common.dto.SkuInfoDTO;
import com.eshop.common.result.Result;
import com.eshop.product.service.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SKU 内部接口（服务间调用）
 * <p>
 * 路径统一 /internal/ 前缀（网关对该前缀一律 404，不经网关暴露）；
 * 仅供 eshop-cart / eshop-order（在架）与 eshop-stock 管理端（includeOffline=true）调用。
 */
@Tag(name = "SKU（内部）", description = "SKU 信息批量查询")
@RestController
@RequestMapping("/internal/sku")
@RequiredArgsConstructor
public class SkuController {

    private final ProductService productService;

    /** 批量查询 SKU 信息（includeOffline=true 时含下架 SKU，管理端场景用） */
    @PostMapping("/batch")
    public Result<List<SkuInfoDTO>> batch(@RequestBody List<Long> skuIds,
                                          @RequestParam(value = "includeOffline", defaultValue = "false") boolean includeOffline) {
        return Result.success(productService.listSkuInfo(skuIds, includeOffline));
    }
}
