package com.eshop.order.mapper;

import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单统计 Mapper（管理端看板，P3；SQL 见 resources/mapper/OrderStatsMapper.xml）
 * <p>
 * 成交口径：status ∈ (1 已支付, 2 已发货, 3 已完成)。
 */
public interface OrderStatsMapper {

    /** 自某时间以来的成交金额 */
    BigDecimal sumPaidAmountSince(@Param("since") LocalDateTime since);

    /** 自某时间以来的订单数（全状态） */
    Long countSince(@Param("since") LocalDateTime since);

    /** 按状态计数 */
    Long countByStatus(@Param("status") Integer status);

    /** 累计成交金额 */
    BigDecimal sumPaidAmountAll();

    /** 订单状态分布（status, cnt） */
    List<Map<String, Object>> countGroupByStatus();

    /** 自某时间以来按天的销售趋势（d, amount, orders） */
    List<Map<String, Object>> salesTrend(@Param("since") LocalDateTime since);

    /** 热销商品 TOP5（name, qty, amount） */
    List<Map<String, Object>> hotTop();
}
