package com.eshop.user.controller;

import com.eshop.common.constant.AuthConstants;
import com.eshop.common.result.Result;
import com.eshop.user.dto.AddressRequest;
import com.eshop.user.service.AddressService;
import com.eshop.user.vo.AddressVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货地址接口（需登录；用户 ID 由网关校验 JWT 后经 X-User-Id 透传）
 */
@Tag(name = "收货地址", description = "收货地址管理（需登录）")
@RestController
@RequestMapping("/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    /** 地址列表 */
    @GetMapping
    public Result<List<AddressVO>> list(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId) {
        return Result.success(addressService.list(userId));
    }

    /** 新增地址（返回地址 ID） */
    @PostMapping
    public Result<Long> create(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                               @Valid @RequestBody AddressRequest request) {
        return Result.success(addressService.create(userId, request));
    }

    /** 修改地址 */
    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                               @PathVariable Long id,
                               @Valid @RequestBody AddressRequest request) {
        addressService.update(userId, id, request);
        return Result.success();
    }

    /** 删除地址 */
    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                               @PathVariable Long id) {
        addressService.delete(userId, id);
        return Result.success();
    }
}
