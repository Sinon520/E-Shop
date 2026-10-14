package com.eshop.stock.client;

import com.eshop.common.dto.SkuInfoDTO;
import com.eshop.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 商品服务内部接口客户端（Nacos 服务发现 + 负载均衡直连，不经网关）
 */
@FeignClient(name = "eshop-product", path = "/internal/sku", contextId = "stockProductSkuClient")
public interface ProductSkuClient {

    /** 批量查询 SKU 信息（管理端场景 includeOffline=true 含下架 SKU） */
    @PostMapping("/batch")
    Result<List<SkuInfoDTO>> batchGetSkuInfo(@RequestBody List<Long> skuIds,
                                             @RequestParam("includeOffline") boolean includeOffline);
}
