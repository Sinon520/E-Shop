package com.eshop.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eshop.common.exception.BizException;
import com.eshop.common.result.ResultCode;
import com.eshop.common.util.JwtUtil;
import com.eshop.user.dto.LoginRequest;
import com.eshop.user.dto.RegisterRequest;
import com.eshop.user.entity.User;
import com.eshop.user.mapper.UserMapper;
import com.eshop.user.vo.LoginVO;
import com.eshop.user.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 认证服务：注册 / 登录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 注册：用户名唯一（先查后插 + 唯一索引兜底并发）
     */
    public void register(RegisterRequest request) {
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        // 默认昵称 = 用户名，用户可在个人中心修改
        user.setNickname(request.getUsername());
        user.setPhone(StringUtils.hasText(request.getPhone()) ? request.getPhone() : null);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发注册撞唯一索引：同样按"用户名已存在"返回
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }
        log.info("用户注册成功: id={}, username={}", user.getId(), user.getUsername());
    }

    /**
     * 登录：校验密码（1002）与账号状态（1003），签发 JWT
     */
    public LoginVO login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        // 用户不存在与密码错误共用同一提示，避免暴露账号是否存在
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (user.getStatus() == null || user.getStatus() == 0) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }

        LoginVO vo = new LoginVO();
        vo.setToken(jwtUtil.generateToken(user.getId(), user.getUsername()));
        vo.setExpiresIn(jwtUtil.getExpireSeconds());

        UserBriefVO brief = new UserBriefVO();
        brief.setId(user.getId());
        brief.setUsername(user.getUsername());
        brief.setNickname(user.getNickname());
        brief.setAvatar(user.getAvatar());
        vo.setUser(brief);

        log.info("用户登录成功: id={}, username={}", user.getId(), user.getUsername());
        return vo;
    }
}
