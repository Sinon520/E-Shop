package com.eshop.user.controller;

import com.eshop.common.constant.AuthConstants;
import com.eshop.common.result.Result;
import com.eshop.user.service.UserService;
import com.eshop.user.vo.ProfileVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户资料接口（需登录；用户 ID 由网关校验 JWT 后经 X-User-Id 透传）
 */
@Tag(name = "用户", description = "用户资料")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 当前用户信息 */
    @GetMapping("/profile")
    public Result<ProfileVO> profile(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId) {
        return Result.success(userService.getProfile(userId));
    }
}
