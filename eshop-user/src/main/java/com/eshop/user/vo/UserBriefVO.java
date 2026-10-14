package com.eshop.user.vo;

import lombok.Data;

/**
 * 用户简要信息（登录响应内嵌）
 */
@Data
public class UserBriefVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;
}
