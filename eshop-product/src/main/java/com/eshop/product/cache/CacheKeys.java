package com.eshop.product.cache;

/**
 * 商品域缓存 key 常量（统一前缀 product:）
 */
public final class CacheKeys {

    /** SPU 详情缓存：product:spu:detail:{spuId} */
    public static final String SPU_DETAIL_PREFIX = "product:spu:detail:";

    /** 分类列表缓存：product:category:list */
    public static final String CATEGORY_LIST = "product:category:list";

    /** SPU 布隆过滤器：product:bloom:spu */
    public static final String SPU_BLOOM = "product:bloom:spu";

    /** SPU 详情重建互斥锁：product:lock:spu:{spuId} */
    public static final String SPU_LOCK_PREFIX = "product:lock:spu:";

    /** 空值标记：缓存"DB 不存在/已下架"的结果，防止恶意请求反复穿透（布隆之外的第二层） */
    public static final String NULL_MARKER = "__NULL__";

    private CacheKeys() {
    }
}
