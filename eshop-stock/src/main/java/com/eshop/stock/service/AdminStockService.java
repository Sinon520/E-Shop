package com.eshop.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eshop.common.dto.SkuInfoDTO;
import com.eshop.common.exception.BizException;
import com.eshop.common.result.PageResult;
import com.eshop.common.result.ResultCode;
import com.eshop.common.util.ResultHelper;
import com.eshop.stock.cache.StockRedisGate;
import com.eshop.stock.client.ProductSkuClient;
import com.eshop.stock.dto.StockAdjustRequest;
import com.eshop.stock.entity.Stock;
import com.eshop.stock.entity.StockLog;
import com.eshop.stock.mapper.StockLogMapper;
import com.eshop.stock.mapper.StockMapper;
import com.eshop.stock.vo.AdminStockVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端库存服务（P3）：库存总览（含 Redis 预扣值核对） / 管理员调整库存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStockService {

    /** 低库存阈值（列表高亮与筛选口径） */
    public static final int LOW_STOCK_THRESHOLD = 20;

    private final StockMapper stockMapper;
    private final StockLogMapper stockLogMapper;
    private final StockRedisGate stockRedisGate;
    private final ProductSkuClient productSkuClient;

    /**
     * 库存分页（管理端）
     * <p>
     * 说明：库存为 SKU 粒度且数据量小，全量查询 + 内存过滤分页；商品名经内部接口补充（含下架 SKU）；
     * 大数量级场景可改为 DB 分页 + 检索服务。
     */
    public PageResult<AdminStockVO> page(long page, long size, String keyword, Boolean lowStock) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;

        List<Stock> all = stockMapper.selectList(new LambdaQueryWrapper<Stock>()
                .orderByAsc(Stock::getSkuId));
        if (all.isEmpty()) {
            return PageResult.of(0, safePage, safeSize, List.of());
        }

        Map<Long, SkuInfoDTO> skuMap = fetchSkuMap(all.stream().map(Stock::getSkuId).toList());

        List<AdminStockVO> filtered = all.stream()
                .map(stock -> toVO(stock, skuMap.get(stock.getSkuId())))
                .filter(vo -> match(vo, kw, Boolean.TRUE.equals(lowStock)))
                .toList();

        int total = filtered.size();
        int from = (int) Math.min((safePage - 1) * safeSize, total);
        int to = (int) Math.min(from + safeSize, total);
        return PageResult.of(total, safePage, safeSize, new ArrayList<>(filtered.subList(from, to)));
    }

    /**
     * 调整库存：CAS（version）更新 + 流水留痕（type=4）+ Redis 预扣值同步
     */
    @Transactional(rollbackFor = Exception.class)
    public void adjust(Long skuId, StockAdjustRequest request) {
        Stock stock = stockMapper.selectOne(new LambdaQueryWrapper<Stock>()
                .eq(Stock::getSkuId, skuId));
        if (stock == null) {
            throw new BizException(ResultCode.SKU_NOT_FOUND);
        }

        boolean absolute = "absolute".equalsIgnoreCase(request.getMode());
        int newAvailable = absolute ? request.getValue() : stock.getAvailable() + request.getValue();
        if (newAvailable < 0) {
            throw new BizException(ResultCode.VALIDATE_FAILED.getCode(), "调整后可售库存不能为负");
        }
        if (newAvailable == stock.getAvailable()) {
            return;
        }

        int rows = stockMapper.update(null, new LambdaUpdateWrapper<Stock>()
                .set(Stock::getAvailable, newAvailable)
                .set(Stock::getVersion, stock.getVersion() + 1)
                .eq(Stock::getSkuId, skuId)
                .eq(Stock::getVersion, stock.getVersion()));
        if (rows == 0) {
            throw new BizException(ResultCode.VALIDATE_FAILED.getCode(), "库存已被并发修改，请重试");
        }

        StockLog logRow = new StockLog();
        logRow.setSkuId(skuId);
        logRow.setOrderNo("ADMIN-" + System.currentTimeMillis());
        logRow.setType(StockLog.TYPE_ADJUST);
        logRow.setQuantity(newAvailable - stock.getAvailable());
        logRow.setBeforeAvailable(stock.getAvailable());
        logRow.setAfterAvailable(newAvailable);
        logRow.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : "管理员调整");
        stockLogMapper.insert(logRow);

        // Redis 预扣值同步（与 DB 一致；若本事务回滚产生漂移，由预热校准收敛）
        stockRedisGate.sync(skuId, newAvailable);
        log.info("管理端调整库存: skuId={}, {} -> {}, remark={}",
                skuId, stock.getAvailable(), newAvailable, logRow.getRemark());
    }

    // ==================== 内部工具 ====================

    /** 补充商品展示信息（含下架 SKU）；失败降级为空（不阻断库存管理） */
    private Map<Long, SkuInfoDTO> fetchSkuMap(List<Long> skuIds) {
        try {
            List<SkuInfoDTO> list = ResultHelper.unwrap(
                    productSkuClient.batchGetSkuInfo(skuIds, true));
            Map<Long, SkuInfoDTO> map = new HashMap<>();
            if (list != null) {
                list.forEach(dto -> map.put(dto.getSkuId(), dto));
            }
            return map;
        } catch (Exception e) {
            log.warn("补充 SKU 展示信息失败（降级为空）: {}", e.getMessage());
            return Map.of();
        }
    }

    private boolean match(AdminStockVO vo, String kw, boolean lowOnly) {
        if (lowOnly && vo.getAvailable() >= LOW_STOCK_THRESHOLD) {
            return false;
        }
        if (kw == null) {
            return true;
        }
        return String.valueOf(vo.getSkuId()).contains(kw)
                || (vo.getSpuName() != null && vo.getSpuName().contains(kw))
                || (vo.getSkuName() != null && vo.getSkuName().contains(kw));
    }

    private AdminStockVO toVO(Stock stock, SkuInfoDTO sku) {
        AdminStockVO vo = new AdminStockVO();
        vo.setSkuId(stock.getSkuId());
        if (sku != null) {
            vo.setSpuName(sku.getSpuName());
            vo.setSkuName(sku.getSkuName());
            vo.setPrice(sku.getPrice());
        }
        vo.setAvailable(stock.getAvailable());
        vo.setLocked(stock.getLocked());
        vo.setSold(stock.getSold());
        Long redis = stockRedisGate.get(stock.getSkuId());
        vo.setRedisValue(redis);
        vo.setConsistent(redis != null && redis.intValue() == stock.getAvailable());
        return vo;
    }
}
