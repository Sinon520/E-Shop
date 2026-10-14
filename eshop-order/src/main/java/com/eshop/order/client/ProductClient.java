package com.eshop.order.client;

import com.eshop.common.dto.SkuInfoDTO;
import com.eshop.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 商品服务内部接口客户端（批量取 SKU 快照信息）
 */
@FeignClient(name = "eshop-product", path = "/internal/sku", contextId = "orderProductClient")
public interface ProductClient {

    @PostMapping("/batch")
    Result<List<SkuInfoDTO>> batchGetSkuInfo(@RequestBody List<Long> skuIds);
}
