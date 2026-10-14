package com.eshop.product.controller;

import com.eshop.common.result.PageResult;
import com.eshop.common.result.Result;
import com.eshop.product.cache.ProductCacheService;
import com.eshop.product.service.ProductService;
import com.eshop.product.vo.SpuDetailVO;
import com.eshop.product.vo.SpuListItemVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品接口（GET /api/product/spu/**，免鉴权）
 */
@Tag(name = "商品", description = "商品列表 / 商品详情")
@RestController
@RequestMapping("/spu")
@RequiredArgsConstructor
public class SpuController {

    private final ProductService productService;
    private final ProductCacheService productCacheService;

    /**
     * 商品列表（分页 / 筛选 / 排序 / 搜索）
     *
     * @param sort default（上新优先）/ sales / priceAsc / priceDesc
     */
    @GetMapping("/page")
    public Result<PageResult<SpuListItemVO>> page(@RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "12") long size,
                                                  @RequestParam(required = false) Long categoryId,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String sort) {
        return Result.success(productService.pageSpu(page, size, categoryId, keyword, sort));
    }

    /** 商品详情（缓存读路径：Cache Aside + 布隆/空值/互斥锁重建） */
    @GetMapping("/{id}")
    public Result<SpuDetailVO> detail(@PathVariable Long id) {
        return Result.success(productCacheService.getSpuDetail(id));
    }
}
