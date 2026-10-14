package com.eshop.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具（jjwt 0.12.x API）
 * <p>
 * 由使用方以密钥 + 有效期构造为 Spring Bean（见 eshop-user 的 JwtConfig），
 * 刻意不注册为组件——否则所有扫描 com.eshop 的服务都会实例化，并要求各自配置密钥。
 * <p>
 * 约定：subject = userId（字符串），附带 username 声明；HS256 签名，密钥长度须 ≥ 32 字节。
 */
public class JwtUtil {

    private final SecretKey key;
    private final long expireSeconds;

    public JwtUtil(String secret, long expireSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    /**
     * 签发 token
     *
     * @param userId   用户 ID
     * @param username 用户名（写入 username 声明）
     */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireSeconds * 1000L))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 token（签名 / 过期），非法时抛出 {@link io.jsonwebtoken.JwtException}
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /**
     * 解析出用户 ID（token 合法时）
     */
    public Long parseUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }
}
