package com.eshop.gateway.support;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT 校验器（网关侧独立实现，密钥与 eshop-user 签发端一致）
 * <p>
 * 网关基于 WebFlux 且按工程约定不依赖 eshop-common，故此处为独立实现，
 * 算法/密钥约定见 docs/api-contract.md §1.4。
 */
@Component
public class JwtVerifier {

    private final SecretKey key;

    public JwtVerifier(@Value("${eshop.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 校验 token（签名 / 有效期），通过则返回用户 ID；无效返回 null
     */
    public Long verifyAndGetUserId(String token) {
        Claims claims = verify(token);
        if (claims == null) {
            return null;
        }
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析 token 的 claims（含 role 等声明）；签名或有效期校验失败返回 null
     */
    public Claims verify(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
