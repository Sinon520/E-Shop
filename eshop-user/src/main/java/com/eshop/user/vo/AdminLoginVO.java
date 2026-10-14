package com.eshop.user.vo;

import lombok.Data;

/**
 * 管理员登录响应
 */
@Data
public class AdminLoginVO {

    /** JWT（携带 role=admin 声明） */
    private String token;

    /** 有效期（秒） */
    private long expiresIn;

    /** 管理员显示名 */
    private String name;
}
