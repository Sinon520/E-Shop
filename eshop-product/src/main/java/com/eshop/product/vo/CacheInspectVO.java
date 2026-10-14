package com.eshop.product.vo;

import lombok.Data;

/**
 * 缓存状态查看结果（内部运维/验收接口 GET /internal/cache/spu/{id}）
 */
@Data
public class CacheInspectVO {

    /** 缓存 key */
    private String key;

    /** key 是否存在 */
    private boolean exists;

    /** 剩余存活毫秒（-2 键不存在 / -1 无过期） */
    private long ttlMillis;

    /** 是否为空值标记（防穿透缓存） */
    private boolean nullMarker;

    /** 内容长度 */
    private int contentLength;

    /** 内容预览（截断至 200 字符） */
    private String preview;
}
