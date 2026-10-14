package com.eshop.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建订单请求（POST /api/order/create）
 * <p>
 * items 由前端从购物车勾选项（或详情页立即购买）整理后提交；
 * 同一 SKU 出现多次时服务端按数量累加合并。
 */
@Data
public class OrderCreateRequest {

    @NotNull(message = "收货地址不能为空")
    private Long addressId;

    @Size(max = 255, message = "备注过长")
    private String remark;

    @NotEmpty(message = "下单商品不能为空")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {

        @NotNull(message = "skuId 不能为空")
        private Long skuId;

        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量不能小于 1")
        @Max(value = 99, message = "数量不能超过 99")
        private Integer quantity;
    }
}
