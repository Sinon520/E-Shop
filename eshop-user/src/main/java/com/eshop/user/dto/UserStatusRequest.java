package com.eshop.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员调整用户状态请求
 */
@Data
public class UserStatusRequest {

    /** 1 正常 / 0 禁用 */
    @NotNull(message = "status 不能为空")
    private Integer status;
}
