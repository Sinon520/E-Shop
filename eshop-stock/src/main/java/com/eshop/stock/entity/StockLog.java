package com.eshop.stock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存流水表 t_stock_log（锁定 / 确认扣减 / 回补 全量留痕）
 * <p>
 * 幂等键 = (order_no, sku_id, type)：同一订单对同一 SKU 的同类操作只生效一次。
 */
@Data
@TableName("t_stock_log")
public class StockLog {

    /** 类型：锁定 */
    public static final int TYPE_LOCK = 1;

    /** 类型：确认扣减 */
    public static final int TYPE_CONFIRM = 2;

    /** 类型：回补 */
    public static final int TYPE_RESTORE = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long skuId;

    private String orderNo;

    /** 类型：1 锁定 / 2 确认扣减 / 3 回补 */
    private Integer type;

    private Integer quantity;

    private Integer beforeAvailable;

    private Integer afterAvailable;

    private String remark;

    private LocalDateTime createTime;
}
