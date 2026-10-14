package com.eshop.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eshop.common.dto.StockOpRequest;
import com.eshop.common.exception.BizException;
import com.eshop.common.result.ResultCode;
import com.eshop.stock.cache.StockRedisGate;
import com.eshop.stock.entity.Stock;
import com.eshop.stock.entity.StockLog;
import com.eshop.stock.mapper.StockLogMapper;
import com.eshop.stock.mapper.StockMapper;
import com.eshop.stock.vo.StockVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存服务：查询 + 内部操作（锁定 / 确认扣减 / 回补）
 * <p>
 * 一致性设计（P2 双层防超卖 + 全局事务）：
 * - 第一层（抗并发入口）：Redis Lua 原子预扣（{@link StockRedisGate}），热点流量在闸门处拦截/放行；
 * - 第二层（最终账本）：DB 乐观锁（version 字段，WHERE version = ? AND available >= ?），绝对不超卖；
 * - 幂等：库存流水 (order_no, sku_id, type) 去重，重复请求直接跳过；
 * - 原子性：单次调用内多 SKU 用本地事务收敛（失败时补偿回滚已成功的 Redis 预扣）；
 * - 跨服务：锁定 / 回补经 Seata AT 参与订单全局事务（undo_log 兜底回滚）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    /** 乐观锁冲突重试次数 */
    private static final int LOCK_MAX_ATTEMPTS = 3;

    private final StockMapper stockMapper;
    private final StockLogMapper stockLogMapper;
    private final StockRedisGate stockRedisGate;

    /**
     * 查询 SKU 可用库存；无库存记录视为 SKU 不存在（3001）
     */
    public StockVO getAvailable(Long skuId) {
        Stock stock = requireStock(skuId);
        StockVO vo = new StockVO();
        vo.setSkuId(skuId);
        vo.setAvailable(stock.getAvailable());
        return vo;
    }

    /**
     * 锁定库存：available -= n，locked += n（幂等；库存不足抛 3002）
     * <p>
     * 双层防超卖：先 Redis Lua 原子预扣（第一层闸门），再 DB 乐观锁扣减（第二层账本）；
     * 任一环节失败 → DB 由事务回滚、Redis 预扣补偿回滚，两侧收敛。
     */
    @Transactional(rollbackFor = Exception.class)
    public void lock(StockOpRequest request) {
        validate(request);
        List<StockOpRequest.Item> deducted = new ArrayList<>();
        try {
            for (StockOpRequest.Item item : request.getItems()) {
                if (logExists(request.getOrderNo(), item.getSkuId(), StockLog.TYPE_LOCK)) {
                    log.info("库存锁定已存在，跳过: orderNo={}, skuId={}", request.getOrderNo(), item.getSkuId());
                    continue;
                }
                // ① 第一层：Redis Lua 原子预扣（抗并发入口；余量不足直接拒绝，不打 DB）
                StockRedisGate.DeductResult result = stockRedisGate.deduct(item.getSkuId(), item.getQuantity());
                if (result == StockRedisGate.DeductResult.INSUFFICIENT) {
                    throw new BizException(ResultCode.STOCK_INSUFFICIENT);
                }
                if (result == StockRedisGate.DeductResult.SUCCESS) {
                    deducted.add(item);
                }
                // BYPASS（未预热 / Redis 异常）→ 降级放行，由第二层 DB 兜底
                // ② 第二层：DB 乐观锁扣减（最终账本）
                lockOne(request.getOrderNo(), item);
            }
        } catch (Exception e) {
            // DB 由本地事务 / Seata 全局事务回滚；Redis 预扣在此补偿回滚（best-effort）
            for (StockOpRequest.Item item : deducted) {
                stockRedisGate.increase(item.getSkuId(), item.getQuantity());
            }
            throw e;
        }
    }

    /**
     * 确认扣减：locked -= n，sold += n（支付成功后调用；幂等）
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(StockOpRequest request) {
        validate(request);
        for (StockOpRequest.Item item : request.getItems()) {
            if (logExists(request.getOrderNo(), item.getSkuId(), StockLog.TYPE_CONFIRM)) {
                log.info("库存确认已存在，跳过: orderNo={}, skuId={}", request.getOrderNo(), item.getSkuId());
                continue;
            }
            confirmOne(request.getOrderNo(), item);
        }
    }

    /**
     * 回补库存：available += n，locked -= n（取消 / 超时关单时调用；幂等）
     * <p>
     * 对"无锁定量可回补"的条目（如锁定失败后已局部回滚）做收敛式跳过并留痕，保证重复调用安全。
     */
    @Transactional(rollbackFor = Exception.class)
    public void restore(StockOpRequest request) {
        validate(request);
        for (StockOpRequest.Item item : request.getItems()) {
            if (logExists(request.getOrderNo(), item.getSkuId(), StockLog.TYPE_RESTORE)) {
                log.info("库存回补已存在，跳过: orderNo={}, skuId={}", request.getOrderNo(), item.getSkuId());
                continue;
            }
            restoreOne(request.getOrderNo(), item);
        }
    }

    // ==================== 单项操作 ====================

    private void lockOne(String orderNo, StockOpRequest.Item item) {
        Long skuId = item.getSkuId();
        int quantity = item.getQuantity();
        for (int attempt = 1; attempt <= LOCK_MAX_ATTEMPTS; attempt++) {
            Stock stock = requireStock(skuId);
            if (stock.getAvailable() < quantity) {
                throw new BizException(ResultCode.STOCK_INSUFFICIENT);
            }
            int rows = stockMapper.update(null, new LambdaUpdateWrapper<Stock>()
                    .set(Stock::getAvailable, stock.getAvailable() - quantity)
                    .set(Stock::getLocked, stock.getLocked() + quantity)
                    .set(Stock::getVersion, stock.getVersion() + 1)
                    .eq(Stock::getSkuId, skuId)
                    .eq(Stock::getVersion, stock.getVersion())
                    .ge(Stock::getAvailable, quantity));
            if (rows == 1) {
                insertLog(orderNo, skuId, StockLog.TYPE_LOCK, quantity,
                        stock.getAvailable(), stock.getAvailable() - quantity, "锁定库存");
                return;
            }
            // 版本冲突（并发扣减），重试
            log.warn("库存锁定版本冲突，重试 {}/{}: orderNo={}, skuId={}", attempt, LOCK_MAX_ATTEMPTS, orderNo, skuId);
        }
        throw new BizException(ResultCode.STOCK_INSUFFICIENT.getCode(), "库存锁定冲突，请稍后重试");
    }

    private void confirmOne(String orderNo, StockOpRequest.Item item) {
        Long skuId = item.getSkuId();
        int quantity = item.getQuantity();
        Stock stock = requireStock(skuId);
        if (stock.getLocked() < quantity) {
            // 付费成功但锁定量异常：fail loudly，等待人工/MQ 补偿（正常链路不会出现）
            throw new BizException(ResultCode.FAILED.getCode(),
                    "库存确认异常：锁定库存不足（orderNo=" + orderNo + ", skuId=" + skuId + "）");
        }
        stockMapper.update(null, new LambdaUpdateWrapper<Stock>()
                .set(Stock::getLocked, stock.getLocked() - quantity)
                .set(Stock::getSold, stock.getSold() + quantity)
                .set(Stock::getVersion, stock.getVersion() + 1)
                .eq(Stock::getSkuId, skuId)
                .ge(Stock::getLocked, quantity));
        insertLog(orderNo, skuId, StockLog.TYPE_CONFIRM, quantity,
                stock.getAvailable(), stock.getAvailable(), "确认扣减");
    }

    private void restoreOne(String orderNo, StockOpRequest.Item item) {
        Long skuId = item.getSkuId();
        int quantity = item.getQuantity();
        Stock stock = requireStock(skuId);
        if (stock.getLocked() < quantity) {
            // 无锁定量可回补（如锁定阶段已回滚）：收敛式跳过并留痕，保证幂等
            log.warn("库存回补跳过（无锁定量）: orderNo={}, skuId={}, locked={}, need={}",
                    orderNo, skuId, stock.getLocked(), quantity);
            insertLog(orderNo, skuId, StockLog.TYPE_RESTORE, 0,
                    stock.getAvailable(), stock.getAvailable(), "无锁定库存可回补，跳过");
            return;
        }
        stockMapper.update(null, new LambdaUpdateWrapper<Stock>()
                .set(Stock::getAvailable, stock.getAvailable() + quantity)
                .set(Stock::getLocked, stock.getLocked() - quantity)
                .set(Stock::getVersion, stock.getVersion() + 1)
                .eq(Stock::getSkuId, skuId)
                .ge(Stock::getLocked, quantity));
        insertLog(orderNo, skuId, StockLog.TYPE_RESTORE, quantity,
                stock.getAvailable(), stock.getAvailable() + quantity, "回补库存");
        // 同步恢复 Redis 可售余量（best-effort；重复调用被上方流水去重拦截，不会重复加回）
        stockRedisGate.increase(skuId, quantity);
    }

    // ==================== Redis 预热 / 核对 ====================

    /**
     * Redis 库存预热：以 DB 为准全量重建（启动时自动执行；同时承担对账校准职责——
     * Seata 全局回滚等不经过本服务的路径造成的短暂漂移，在此收敛）
     */
    public String preheatRedis() {
        List<Stock> stocks = stockMapper.selectList(null);
        for (Stock stock : stocks) {
            stockRedisGate.sync(stock.getSkuId(), stock.getAvailable());
        }
        String summary = "Redis 库存预热完成：" + stocks.size() + " 个 SKU";
        log.info("[库存] {}", summary);
        return summary;
    }

    /**
     * 核对某 SKU 的 Redis 预扣值与 DB 账本（内部接口用）
     */
    public Map<String, Object> inspectRedis(Long skuId) {
        Stock stock = requireStock(skuId);
        Long redisValue = stockRedisGate.get(skuId);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("skuId", skuId);
        map.put("dbAvailable", stock.getAvailable());
        map.put("redisValue", redisValue);
        map.put("consistent", redisValue != null && redisValue.intValue() == stock.getAvailable());
        return map;
    }

    // ==================== 内部工具 ====================

    private void validate(StockOpRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrderNo())
                || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BizException(ResultCode.VALIDATE_FAILED.getCode(), "库存操作参数不完整");
        }
        for (StockOpRequest.Item item : request.getItems()) {
            if (item.getSkuId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BizException(ResultCode.VALIDATE_FAILED.getCode(), "库存操作参数不完整");
            }
        }
    }

    private Stock requireStock(Long skuId) {
        Stock stock = stockMapper.selectOne(new LambdaQueryWrapper<Stock>().eq(Stock::getSkuId, skuId));
        if (stock == null) {
            throw new BizException(ResultCode.SKU_NOT_FOUND);
        }
        return stock;
    }

    private boolean logExists(String orderNo, Long skuId, int type) {
        List<StockLog> logs = stockLogMapper.selectList(new LambdaQueryWrapper<StockLog>()
                .eq(StockLog::getOrderNo, orderNo)
                .eq(StockLog::getSkuId, skuId)
                .eq(StockLog::getType, type)
                .last("LIMIT 1"));
        return !logs.isEmpty();
    }

    private void insertLog(String orderNo, Long skuId, int type, int quantity,
                           int beforeAvailable, int afterAvailable, String remark) {
        StockLog stockLog = new StockLog();
        stockLog.setOrderNo(orderNo);
        stockLog.setSkuId(skuId);
        stockLog.setType(type);
        stockLog.setQuantity(quantity);
        stockLog.setBeforeAvailable(beforeAvailable);
        stockLog.setAfterAvailable(afterAvailable);
        stockLog.setRemark(remark);
        stockLogMapper.insert(stockLog);
    }
}
