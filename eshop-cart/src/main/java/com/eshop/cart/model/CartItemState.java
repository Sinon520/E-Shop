package com.eshop.cart.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 购物车条目状态（Redis Hash 的 value，JSON 序列化）
 * <p>
 * Redis 结构：key = cart:{userId}，field = skuId，value = 本对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemState {

    /** 数量（1-99） */
    private Integer quantity;

    /** 是否勾选 */
    private Boolean checked;

    /** 加购时间戳（毫秒，用于列表排序） */
    private Long addTime;
}
