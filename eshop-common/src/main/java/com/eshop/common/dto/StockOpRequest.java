package com.eshop.common.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 库存操作请求（锁定 / 确认扣减 / 回补 共用一个结构）
 * <p>
 * 传输方向：eshop-order / eshop-pay → eshop-stock 内部接口（POST /internal/stock/*）
 * <p>
 * orderNo 为幂等键：库存流水表按 (order_no, sku_id, type) 去重，重复请求直接跳过。
 */
@Data
public class StockOpRequest implements Serializable {

    /** 关联订单号 */
    private String orderNo;

    /** 操作明细 */
    private List<Item> items;

    @Data
    public static class Item implements Serializable {

        private Long skuId;

        private Integer quantity;
    }
}
