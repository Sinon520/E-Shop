package com.eshop.product.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ES 搜索配置（eshop.es.*）
 */
@Data
@ConfigurationProperties(prefix = "eshop.es")
public class EsProperties {

    /** 总开关：false 时不创建客户端，搜索直接走 DB 降级 */
    private boolean enabled = false;

    /** ES 地址（host:port） */
    private String host = "127.0.0.1:9200";

    private String scheme = "http";

    /** SPU 索引名 */
    private String index = "eshop_spu";

    /** 启动时异步全量重建索引 */
    private boolean startupReindex = true;
}
