package com.eshop.common.constant;

/**
 * 认证相关常量（网关与各服务共用的约定，见 docs/api-contract.md §1.4）
 */
public final class AuthConstants {

    private AuthConstants() {
    }

    /** 请求头：Authorization: Bearer &lt;token&gt; */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /** 网关校验 JWT 后注入的内部请求头：用户 ID（下游服务只信任此头） */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** token 前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";
}
