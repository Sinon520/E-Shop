package com.eshop.user.vo;

import lombok.Data;

/**
 * 管理端用户列表项
 */
@Data
public class AdminUserVO {

    private Long id;

    private String username;

    private String nickname;

    /** 脱敏手机号 */
    private String phone;

    /** 注册时间（yyyy-MM-dd HH:mm:ss） */
    private String createTime;

    /** 1 正常 / 0 禁用 */
    private Integer status;
}
