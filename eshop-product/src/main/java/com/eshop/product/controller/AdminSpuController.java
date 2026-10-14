package com.eshop.product.controller;

import com.eshop.common.result.PageResult;
import com.eshop.common.result.Result;
import com.eshop.product.dto.SpuSaveRequest;
import com.eshop.product.dto.SpuStatusRequest;
import com.eshop.product.service.AdminProductService;
import com.eshop.product.vo.AdminSpuVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端商品接口（管理后台，P3；网关校验 role=admin）
 */
@Tag(name = "管理端-商品", description = "商品列表 / 上下架 / 新增编辑")
@RestController
@RequestMapping("/admin/product/spu")
@RequiredArgsConstructor
public class AdminSpuController {

    private final AdminProductService adminProductService;

    /** 商品分页（含下架） */
    @GetMapping("/page")
    public Result<PageResult<AdminSpuVO>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Long categoryId,
                                               @RequestParam(required = false) Integer status) {
        return Result.success(adminProductService.spuPage(page, size, keyword, categoryId, status));
    }

    /** 上架 / 下架 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody @Valid SpuStatusRequest request) {
        adminProductService.updateStatus(id, request.getStatus());
        return Result.success();
    }

    /** 新增商品（含初始 SKU 列表） */
    @PostMapping
    public Result<Long> create(@RequestBody @Valid SpuSaveRequest request) {
        return Result.success(adminProductService.createSpu(request));
    }

    /** 编辑商品基本信息 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid SpuSaveRequest request) {
        adminProductService.updateSpu(id, request);
        return Result.success();
    }
}
