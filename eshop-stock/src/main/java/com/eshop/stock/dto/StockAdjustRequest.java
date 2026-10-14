package com.eshop.stock.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理端库存调整请求
 */
@Data
public class StockAdjustRequest {

    /** 调整模式：delta 增量（+/-，默认）/ absolute 绝对值 */
    private String mode;

    /** 数值（delta 模式可负；absolute 模式为目标可售量） */
    @NotNull(message = "value 不能为空")
    private Integer value;

    /** 原因备注 */
    private String remark;
}
