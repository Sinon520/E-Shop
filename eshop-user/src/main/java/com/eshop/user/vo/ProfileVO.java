package com.eshop.user.vo;

import lombok.Data;

/**
 * 当前用户信息（GET /api/user/profile 响应 data）
 */
@Data
public class ProfileVO {

    private Long id;

    private String username;

    private String nickname;

    /** 手机号（脱敏：138****0000） */
    private String phone;

    private String email;

    private String avatar;

    /** yyyy-MM-dd HH:mm:ss */
    private String createTime;
}
