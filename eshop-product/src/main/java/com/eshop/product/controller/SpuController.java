package com.eshop.product.controller;

import com.eshop.common.result.PageResult;
import com.eshop.common.result.Result;
import com.eshop.product.cache.ProductCacheService;
import com.eshop.product.service.ProductService;
import com.eshop.product.service.SpuSearchService;
import com.eshop.product.vo.SpuDetailVO;
import com.eshop.product.vo.SpuListItemVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品接口（GET /api/product/spu/**，免鉴权）
 */
@Slf4j
@Tag(name = "商品", description = "商品列表 / 搜索 / 商品详情")
@RestController
@RequestMapping("/spu")
@RequiredArgsConstructor
public class SpuController {

    private final ProductService productService;
    private final ProductCacheService productCacheService;
    private final ObjectProvider<SpuSearchService> searchServiceProvider;

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

    /**
     * 商品搜索（P4：ES + IK 分词，multi_match 字段加权 + 相关度排序）
     * <p>
     * ES 未启用（eshop.es.enabled=false）或查询异常时降级 DB like 查询，接口契约不变。
     *
     * @param sort default（相关度）/ sales / priceAsc / priceDesc
     */
    @GetMapping("/search")
    public Result<PageResult<SpuListItemVO>> search(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "12") long size,
                                                    @RequestParam(required = false) Long categoryId,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String sort) {
        SpuSearchService searchService = searchServiceProvider.getIfAvailable();
        if (searchService != null) {
            try {
                return Result.success(searchService.search(keyword, page, size, categoryId, sort));
            } catch (Exception e) {
                log.warn("ES 搜索异常，降级 DB 查询: {}", e.getMessage());
            }
        }
        return Result.success(productService.pageSpu(page, size, categoryId, keyword, sort));
    }

    /** 商品详情（缓存读路径：Cache Aside + 布隆/空值/互斥锁重建） */
    @GetMapping("/{id}")
    public Result<SpuDetailVO> detail(@PathVariable Long id) {
        return Result.success(productCacheService.getSpuDetail(id));
    }
}
