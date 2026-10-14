package com.eshop.product.controller;

import com.eshop.common.result.Result;
import com.eshop.product.dto.AdminCategoryRequest;
import com.eshop.product.entity.Category;
import com.eshop.product.service.AdminProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端分类接口（管理后台，P3；网关校验 role=admin）
 */
@Tag(name = "管理端-分类", description = "分类列表 / 新增 / 编辑 / 删除")
@RestController
@RequestMapping("/admin/product/category")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final AdminProductService adminProductService;

    /** 全量分类（含停用） */
    @GetMapping("/list")
    public Result<List<Category>> list() {
        return Result.success(adminProductService.categoryList());
    }

    /** 新增分类 */
    @PostMapping
    public Result<Long> create(@RequestBody @Valid AdminCategoryRequest request) {
        return Result.success(adminProductService.createCategory(request));
    }

    /** 编辑分类 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid AdminCategoryRequest request) {
        adminProductService.updateCategory(id, request);
        return Result.success();
    }

    /** 删除分类（有子分类 / 商品引用时拒绝） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminProductService.deleteCategory(id);
        return Result.success();
    }
}
