package com.eshop.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eshop.common.exception.BizException;
import com.eshop.common.result.ResultCode;
import com.eshop.common.util.JwtUtil;
import com.eshop.user.dto.LoginRequest;
import com.eshop.user.entity.Admin;
import com.eshop.user.mapper.AdminMapper;
import com.eshop.user.vo.AdminLoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 管理员认证服务（管理后台独立体系，P3）
 * <p>
 * 与 C 端用户登录完全隔离：独立账号表 t_admin，签发的 JWT 额外携带 role=admin 声明，
 * 网关对 /api/admin/** 路径校验该声明后方可放行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    /** admin 角色声明值（与网关校验约定一致） */
    private static final String ROLE_ADMIN = "admin";

    private final AdminMapper adminMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 管理员登录：校验密码（1002）与账号状态（1003），签发带 role=admin 的 JWT
     */
    public AdminLoginVO login(LoginRequest request) {
        Admin admin = adminMapper.selectOne(
                new LambdaQueryWrapper<Admin>().eq(Admin::getUsername, request.getUsername()));
        // 不存在与密码错误共用提示，避免暴露账号是否存在
        if (admin == null || !passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new BizException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (admin.getStatus() == null || admin.getStatus() == 0) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }

        AdminLoginVO vo = new AdminLoginVO();
        vo.setToken(jwtUtil.generateToken(admin.getId(), admin.getUsername(), ROLE_ADMIN));
        vo.setExpiresIn(jwtUtil.getExpireSeconds());
        vo.setName(admin.getName());
        log.info("管理员登录成功: id={}, username={}", admin.getId(), admin.getUsername());
        return vo;
    }
}
