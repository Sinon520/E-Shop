package com.eshop.user.controller;

import com.eshop.common.result.Result;
import com.eshop.user.dto.LoginRequest;
import com.eshop.user.dto.RegisterRequest;
import com.eshop.user.service.AuthService;
import com.eshop.user.vo.LoginVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（网关白名单放行，见 docs/api-contract.md §2.1 / §2.2）
 */
@Tag(name = "认证", description = "注册 / 登录（免鉴权）")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 注册 */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success();
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }
}
