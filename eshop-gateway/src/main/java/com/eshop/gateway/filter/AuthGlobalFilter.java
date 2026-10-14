package com.eshop.gateway.filter;

import com.eshop.gateway.support.JwtVerifier;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 全局鉴权过滤器（见 docs/api-contract.md §1.4）
 * <p>
 * 1. 免鉴权白名单放行（register/login、GET 商品、GET 库存、各服务 health、预检 OPTIONS）；
 * 2. 其余请求校验 Authorization: Bearer &lt;token&gt;，失败返回 401 统一 JSON；
 * 3. 校验通过后注入内部请求头 X-User-Id；且任何情况下先剥掉外部传入的 X-User-Id，防止伪造。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String TOKEN_PREFIX = "Bearer ";

    private static final String UNAUTHORIZED_BODY =
            "{\"code\":401,\"message\":\"暂未登录或登录已过期\",\"data\":null}";

    private static final String FORBIDDEN_BODY =
            "{\"code\":403,\"message\":\"无管理员权限\",\"data\":null}";

    /** 管理后台路径前缀：需携带 role=admin 的 JWT（P3） */
    private static final String ADMIN_PATH_PREFIX = "/api/admin/";

    /** 管理员登录（白名单） */
    private static final String ADMIN_LOGIN_PATH = "/api/admin/auth/login";

    /** 管理员角色声明值（与 eshop-user 签发约定一致） */
    private static final String ROLE_ADMIN = "admin";

    private static final String NOT_FOUND_BODY =
            "{\"code\":404,\"message\":\"资源不存在\",\"data\":null}";

    /** 内部接口路径标记：一律不经网关暴露（服务间经 Nacos 直连调用） */
    private static final String INTERNAL_PATH_MARK = "/internal/";

    /** 免鉴权：精确匹配的 POST 接口 */
    private static final List<String> WHITELIST_POST_PATHS =
            List.of("/api/user/auth/register", "/api/user/auth/login");

    /** 免鉴权：GET 前缀（商品浏览、库存查询） */
    private static final List<String> WHITELIST_GET_PREFIXES =
            List.of("/api/product/", "/api/stock/");

    /** 免鉴权：健康检查 GET /api/{service}/health */
    private static final Pattern HEALTH_PATTERN = Pattern.compile("^/api/[^/]+/health$");

    private final JwtVerifier jwtVerifier;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        // 始终剥掉外部传入的 X-User-Id（下游服务只信任本网关注入的值）
        ServerHttpRequest.Builder requestBuilder =
                request.mutate().headers(headers -> headers.remove(HEADER_USER_ID));

        // CORS 预检直接放行
        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }

        // 内部接口统一拦截（优先于白名单判断，防止 GET /api/product/internal/** 之类被白名单放行）
        if (path.contains(INTERNAL_PATH_MARK)) {
            return notFound(exchange, path);
        }

        // 管理员登录白名单
        if (HttpMethod.POST.equals(method) && ADMIN_LOGIN_PATH.equals(path)) {
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }

        // 管理端接口：要求携带 role=admin 的 JWT（先于普通白名单判断）
        if (path.startsWith(ADMIN_PATH_PREFIX)) {
            return filterAdmin(exchange, chain, requestBuilder, request, path);
        }

        // 白名单放行
        if (isWhitelisted(method, path)) {
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }

        String authorization = request.getHeaders().getFirst(HEADER_AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(TOKEN_PREFIX)) {
            return unauthorized(exchange, path, "token 缺失");
        }
        Long userId = jwtVerifier.verifyAndGetUserId(authorization.substring(TOKEN_PREFIX.length()));
        if (userId == null) {
            return unauthorized(exchange, path, "token 无效或已过期");
        }

        // 校验通过：注入内部用户身份头
        requestBuilder.header(HEADER_USER_ID, String.valueOf(userId));
        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }

    private boolean isWhitelisted(HttpMethod method, String path) {
        if (HttpMethod.POST.equals(method) && WHITELIST_POST_PATHS.contains(path)) {
            return true;
        }
        if (HttpMethod.GET.equals(method)) {
            for (String prefix : WHITELIST_GET_PREFIXES) {
                if (path.startsWith(prefix)) {
                    return true;
                }
            }
            return HEALTH_PATTERN.matcher(path).matches();
        }
        return false;
    }

    /** 管理端鉴权：token 有效且 role=admin 才放行；透传管理员 ID 至 X-User-Id（P3） */
    private Mono<Void> filterAdmin(ServerWebExchange exchange, GatewayFilterChain chain,
                                   ServerHttpRequest.Builder requestBuilder,
                                   ServerHttpRequest request, String path) {
        String authorization = request.getHeaders().getFirst(HEADER_AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(TOKEN_PREFIX)) {
            return unauthorized(exchange, path, "admin token 缺失");
        }
        Claims claims = jwtVerifier.verify(authorization.substring(TOKEN_PREFIX.length()));
        if (claims == null) {
            return unauthorized(exchange, path, "admin token 无效或已过期");
        }
        if (!ROLE_ADMIN.equals(claims.get("role", String.class))) {
            return forbidden(exchange, path);
        }
        requestBuilder.header(HEADER_USER_ID, claims.getSubject());
        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String path) {
        log.debug("管理端权限拦截: path={}", path);
        return writeJson(exchange, HttpStatus.FORBIDDEN, FORBIDDEN_BODY);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String path, String reason) {
        log.debug("鉴权拦截: path={}, reason={}", path, reason);
        return writeJson(exchange, HttpStatus.UNAUTHORIZED, UNAUTHORIZED_BODY);
    }

    private Mono<Void> notFound(ServerWebExchange exchange, String path) {
        log.debug("内部路径拦截: path={}", path);
        return writeJson(exchange, HttpStatus.NOT_FOUND, NOT_FOUND_BODY);
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, String jsonBody) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(jsonBody.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 早于路由级过滤器（StripPrefix 等）执行，确保按“/api/**”原始路径判断白名单
        return -100;
    }
}
