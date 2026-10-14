package com.eshop.order.service;

import com.eshop.common.constant.OrderStatus;
import com.eshop.order.mapper.OrderStatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端数据看板服务（P3）：今日 / 累计成交、订单分布、销售趋势、热销榜
 * <p>
 * 全部为订单域本库聚合（订单快照含商品名），不跨服务查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final OrderStatsMapper orderStatsMapper;

    /**
     * 看板总览
     */
    public Map<String, Object> overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("todaySales", nvl(orderStatsMapper.sumPaidAmountSince(todayStart)));
        map.put("todayOrders", nvl(orderStatsMapper.countSince(todayStart)));
        map.put("pendingShip", nvl(orderStatsMapper.countByStatus(OrderStatus.PAID)));
        map.put("totalSales", nvl(orderStatsMapper.sumPaidAmountAll()));
        map.put("statusDist", orderStatsMapper.countGroupByStatus());
        map.put("trend7d", orderStatsMapper.salesTrend(LocalDate.now().minusDays(6).atStartOfDay()));
        map.put("hotTop5", orderStatsMapper.hotTop());
        return map;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long nvl(Long value) {
        return value == null ? 0L : value;
    }
}
