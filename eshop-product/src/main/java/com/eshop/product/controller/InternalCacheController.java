package com.eshop.product.controller;

import com.eshop.common.result.Result;
import com.eshop.product.cache.ProductCacheService;
import com.eshop.product.vo.CacheInspectVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 缓存治理内部接口（/internal 前缀：网关一律 404，不经网关暴露）
 * <p>
 * 用途：本地验证缓存行为、运维排查（缓存状态查看 / 失效 / 预热）。
 */
@Tag(name = "缓存（内部）", description = "缓存状态查看 / 失效 / 预热")
@RestController
@RequestMapping("/internal/cache")
@RequiredArgsConstructor
public class InternalCacheController {

    private final ProductCacheService productCacheService;

    /** 查看商品详情缓存状态（key / 是否存在 / TTL / 是否空值标记 / 内容预览） */
    @GetMapping("/spu/{spuId}")
    public Result<CacheInspectVO> inspect(@PathVariable Long spuId) {
        return Result.success(productCacheService.inspectSpuDetail(spuId));
    }

    /**
     * 模拟数据变更后的缓存失效链路：删除 + 延迟双删
     * <p>
     * 验证方法：调用后缓存立即消失；若期间有读请求回填缓存，延迟任务会在
     * double-delete-delay-millis 后再次删除——这正是延迟双删的意义。
     *
     * @param doubleDelete 是否附带延迟第二次删除（默认 true；纯删除场景传 false）
     */
    @PostMapping("/spu/{spuId}/evict")
    public Result<String> evict(@PathVariable Long spuId,
                                @RequestParam(defaultValue = "true") boolean doubleDelete) {
        productCacheService.evictSpuDetail(spuId, doubleDelete);
        return Result.success("已删除缓存" + (doubleDelete ? "并投递延迟删除任务" : "") + "：spuId=" + spuId);
    }

    /** 手动触发缓存预热（布隆过滤器 + 详情缓存） */
    @PostMapping("/preheat")
    public Result<String> preheat() {
        return Result.success(productCacheService.preheat());
    }
}
