package com.eshop.product.controller;

import com.eshop.common.result.Result;
import com.eshop.product.service.ProductService;
import com.eshop.product.vo.CategoryVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类接口（GET /api/product/categories，免鉴权）
 */
@Tag(name = "分类", description = "商品分类列表")
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final ProductService productService;

    /** 分类列表 */
    @GetMapping("/categories")
    public Result<List<CategoryVO>> list() {
        return Result.success(productService.listCategories());
    }
}
