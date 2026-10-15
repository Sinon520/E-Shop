package com.eshop.common.dto;

import lombok.Data;

/**
 * 用户基础信息（跨服务批量查询；管理端订单列表回填用户名）
 */
@Data
public class UserBaseDTO {

    private Long userId;

    private String username;
}
