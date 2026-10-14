package com.eshop.user.vo;

import lombok.Data;

/**
 * 登录响应（data 结构，见 docs/api-contract.md §2.2）
 */
@Data
public class LoginVO {

    /** JWT */
    private String token;

    /** 固定 "Bearer" */
    private String tokenType = "Bearer";

    /** 有效期（秒） */
    private long expiresIn;

    /** 用户简要信息 */
    private UserBriefVO user;
}
