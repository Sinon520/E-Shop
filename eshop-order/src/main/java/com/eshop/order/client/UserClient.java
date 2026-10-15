package com.eshop.order.client;

import com.eshop.common.dto.AddressDTO;
import com.eshop.common.dto.UserBaseDTO;
import com.eshop.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 用户服务内部接口客户端（下单取收货地址快照 / 管理端订单列表回填用户名）
 */
@FeignClient(name = "eshop-user", path = "/internal", contextId = "orderUserClient")
public interface UserClient {

    @GetMapping("/addresses/{id}")
    Result<AddressDTO> getAddress(@PathVariable("id") Long id);

    /** 按 userIds 批量查用户基础信息（管理端订单列表展示） */
    @PostMapping("/users/batch")
    Result<List<UserBaseDTO>> batchUsers(@RequestBody List<Long> userIds);
}
