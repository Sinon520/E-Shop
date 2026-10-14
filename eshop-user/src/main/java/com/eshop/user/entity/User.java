package com.eshop.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表 t_user
 */
@Data
@TableName("t_user")
public class User {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名（登录名） */
    private String username;

    /** 密码（BCrypt 哈希） */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 手机号（可空） */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 头像 URL */
    private String avatar;

    /** 状态：1 正常 / 0 禁用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除：0 未删 / 1 已删（逻辑删除由 mybatis-plus 全局配置接管） */
    private Integer deleted;
}
