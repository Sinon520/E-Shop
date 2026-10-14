package com.eshop.order.client;

import com.eshop.common.constant.AuthConstants;
import com.eshop.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 购物车服务客户端（下单成功后移除已下单条目）
 * <p>
 * 复用购物车对用户暴露的批量删除接口，按「信任内网请求头」约定显式携带 X-User-Id。
 */
@FeignClient(name = "eshop-cart", contextId = "orderCartClient")
public interface CartClient {

    @PostMapping("/batch-remove")
    Result<Void> batchRemove(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                             @RequestBody CartRemoveRequest request);
}
