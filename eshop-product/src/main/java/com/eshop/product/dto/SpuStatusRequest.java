package com.eshop.product.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理端商品上下架请求
 */
@Data
public class SpuStatusRequest {

    /** 0 下架 / 1 上架 */
    @NotNull(message = "status 不能为空")
    private Integer status;
}
