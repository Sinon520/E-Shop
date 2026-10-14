package com.eshop.user.config;

import com.eshop.common.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 用户服务组件装配：JWT 工具（密钥注入）与 BCrypt 编码器
 */
@Configuration
public class UserConfig {

    @Bean
    public JwtUtil jwtUtil(@Value("${eshop.jwt.secret}") String secret,
                           @Value("${eshop.jwt.expire-seconds:7200}") long expireSeconds) {
        return new JwtUtil(secret, expireSeconds);
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
