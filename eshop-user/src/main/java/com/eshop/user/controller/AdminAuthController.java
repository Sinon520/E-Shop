package com.eshop.user.controller;

import com.eshop.common.result.Result;
import com.eshop.user.dto.LoginRequest;
import com.eshop.user.service.AdminAuthService;
import com.eshop.user.vo.AdminLoginVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员认证接口（管理后台，P3）
 * <p>
 * 网关策略：/api/admin/auth/login 白名单放行；其余 /api/admin/** 均要求 role=admin 的 JWT。
 */
@Tag(name = "管理员认证", description = "管理后台登录")
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    /** 管理员登录 */
    @PostMapping("/login")
    public Result<AdminLoginVO> login(@RequestBody @Valid LoginRequest request) {
        return Result.success(adminAuthService.login(request));
    }
}
