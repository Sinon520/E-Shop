package com.eshop.user.controller;

import com.eshop.common.dto.AddressDTO;
import com.eshop.common.result.Result;
import com.eshop.user.service.AddressService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户域内部接口（服务间调用）
 * <p>
 * 路径统一 /internal/ 前缀（网关对该前缀一律 404，不经网关暴露）；
 * 供 eshop-order 下单时取收货地址快照。
 */
@Tag(name = "用户（内部）", description = "地址内部查询")
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalUserController {

    private final AddressService addressService;

    /** 按 ID 查收货地址（含 userId，调用方校验归属） */
    @GetMapping("/addresses/{id:\\d+}")
    public Result<AddressDTO> getAddress(@PathVariable Long id) {
        return Result.success(addressService.getInternal(id));
    }
}
