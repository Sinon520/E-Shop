package com.eshop.order.client;

import com.eshop.common.dto.AddressDTO;
import com.eshop.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 用户服务内部接口客户端（下单取收货地址快照）
 */
@FeignClient(name = "eshop-user", path = "/internal", contextId = "orderUserClient")
public interface UserClient {

    @GetMapping("/addresses/{id}")
    Result<AddressDTO> getAddress(@PathVariable("id") Long id);
}
