package com.eshop.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eshop.common.constant.OrderStatus;
import com.eshop.common.dto.AddressDTO;
import com.eshop.common.dto.OrderForPayDTO;
import com.eshop.common.dto.SkuInfoDTO;
import com.eshop.common.dto.StockOpRequest;
import com.eshop.common.exception.BizException;
import com.eshop.common.result.PageResult;
import com.eshop.common.result.ResultCode;
import com.eshop.common.util.ResultHelper;
import com.eshop.order.client.CartClient;
import com.eshop.order.client.CartRemoveRequest;
import com.eshop.order.client.ProductClient;
import com.eshop.order.client.StockClient;
import com.eshop.order.client.UserClient;
import com.eshop.order.dto.OrderCreateRequest;
import com.eshop.order.entity.Order;
import com.eshop.order.entity.OrderItem;
import com.eshop.order.entity.OrderLog;
import com.eshop.order.mapper.OrderItemMapper;
import com.eshop.order.mapper.OrderLogMapper;
import com.eshop.order.mapper.OrderMapper;
import com.eshop.order.vo.OrderCreateVO;
import com.eshop.order.vo.OrderDetailVO;
import com.eshop.order.vo.OrderItemVO;
import com.eshop.order.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 订单服务：下单 / 列表 / 详情 / 取消 + 内部状态流转
 * <p>
 * P1 一致性策略（先跑通，P2 引入亮点）：
 * - 落库（主单 + 明细 + 日志）用本地事务（TransactionTemplate）；
 * - 状态流转用 CAS（WHERE status = 当前值）防并发重复流转；
 * - 「订单 - 库存」跨服务用「先提交 + 失败补偿」：锁库存失败 → 订单置取消 + 尽力回补；
 *   P2 将用 Seata AT 收敛为全局事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter ORDER_NO_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final int MAX_ITEM_QUANTITY = 99;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderLogMapper orderLogMapper;
    private final ProductClient productClient;
    private final StockClient stockClient;
    private final UserClient userClient;
    private final CartClient cartClient;
    private final TransactionTemplate transactionTemplate;

    // ==================== 用户侧 ====================

    /**
     * 创建订单：地址快照 + 商品快照（服务端现价）→ 落库 → 锁库存 → 清理购物车
     */
    public OrderCreateVO create(Long userId, OrderCreateRequest request) {
        // 同 SKU 合并累加
        LinkedHashMap<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderCreateRequest.Item item : request.getItems()) {
            merged.merge(item.getSkuId(), item.getQuantity(), Integer::sum);
        }
        merged.forEach((skuId, quantity) -> {
            if (quantity > MAX_ITEM_QUANTITY) {
                throw new BizException(ResultCode.VALIDATE_FAILED.getCode(), "单个商品数量不能超过 99");
            }
        });

        // 收货地址（含归属校验）
        AddressDTO address = fetchAddress(userId, request.getAddressId());
        // 商品快照（在架校验 + 服务端现价，不信任前端金额）
        Map<Long, SkuInfoDTO> skuMap = fetchSkuMap(merged.keySet());

        String orderNo = generateOrderNo();
        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
            SkuInfoDTO sku = skuMap.get(entry.getKey());
            if (sku == null) {
                throw new BizException(ResultCode.SKU_NOT_FOUND);
            }
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderNo(orderNo);
            orderItem.setSpuId(sku.getSpuId());
            orderItem.setSkuId(sku.getSkuId());
            orderItem.setSpuName(sku.getSpuName());
            orderItem.setSkuName(sku.getSkuName());
            orderItem.setSkuImage(sku.getImage());
            orderItem.setPrice(sku.getPrice());
            orderItem.setQuantity(entry.getValue());
            orderItem.setTotalAmount(sku.getPrice().multiply(BigDecimal.valueOf(entry.getValue())));
            items.add(orderItem);
            total = total.add(orderItem.getTotalAmount());
        }

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(total);
        order.setPayAmount(total);
        order.setStatus(OrderStatus.PENDING_PAY);
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getReceiverPhone());
        order.setReceiverAddress(joinAddress(address));
        order.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : "");

        // 本地事务落库：主单 + 明细 + 状态日志
        transactionTemplate.executeWithoutResult(status -> {
            orderMapper.insert(order);
            items.forEach(orderItemMapper::insert);
            insertLog(orderNo, OrderStatus.INIT, OrderStatus.PENDING_PAY,
                    OrderStatus.OPERATOR_SYSTEM, 0L, "创建订单");
        });

        // 锁定库存：失败则撤销订单并尽力回补
        StockOpRequest stockOp = buildStockOp(orderNo, merged);
        try {
            ResultHelper.unwrap(stockClient.lock(stockOp));
        } catch (Exception e) {
            handleLockFailure(order, stockOp);
            if (e instanceof BizException bizException) {
                throw bizException;
            }
            throw new BizException("下单失败：库存服务暂不可用，请稍后重试");
        }

        // 清理购物车中已下单条目（尽力而为，不影响下单结果）
        try {
            ResultHelper.unwrap(cartClient.batchRemove(userId,
                    new CartRemoveRequest(new ArrayList<>(merged.keySet()))));
        } catch (Exception e) {
            log.warn("下单后清理购物车失败（不影响订单）: orderNo={}, userId={}", orderNo, userId, e);
        }

        log.info("订单创建成功: orderNo={}, userId={}, payAmount={}", orderNo, userId, total);
        return new OrderCreateVO(orderNo, total);
    }

    /** 订单分页（用户维度，可按状态过滤） */
    public PageResult<OrderVO> page(Long userId, long page, long size, Integer status) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);

        IPage<Order> result = orderMapper.selectPage(new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .eq(status != null, Order::getStatus, status)
                        .orderByDesc(Order::getId));
        List<Order> orders = result.getRecords();
        Map<String, List<OrderItem>> itemsMap = loadItemsMap(orders.stream().map(Order::getOrderNo).toList());

        List<OrderVO> list = orders.stream()
                .map(order -> toListVO(order, itemsMap.getOrDefault(order.getOrderNo(), List.of())))
                .toList();
        return PageResult.of(result.getTotal(), safePage, safeSize, list);
    }

    /** 订单详情（归属校验） */
    public OrderDetailVO detail(Long userId, String orderNo) {
        Order order = requireOwned(userId, orderNo);
        List<OrderItem> items = listItems(orderNo);

        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusText(OrderStatus.text(order.getStatus()));
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setRemark(order.getRemark());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        vo.setCreateTime(formatDateTime(order.getCreateTime()));
        vo.setPayTime(formatDateTime(order.getPayTime()));
        vo.setItems(items.stream().map(this::toItemVO).toList());
        return vo;
    }

    /** 取消订单（仅待支付）：状态流转 + 回补库存（幂等） */
    public void cancel(Long userId, String orderNo) {
        Order order = requireOwned(userId, orderNo);
        if (order.getStatus() != OrderStatus.PENDING_PAY) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL);
        }
        transactStatus(order, OrderStatus.CANCELLED, OrderStatus.OPERATOR_USER,
                userId, "用户取消订单", true);

        // 回补库存：幂等；失败留错误日志（P2 由 MQ 补偿承接）
        StockOpRequest stockOp = buildStockOp(orderNo, listItems(orderNo));
        try {
            ResultHelper.unwrap(stockClient.restore(stockOp));
        } catch (Exception e) {
            log.error("取消订单回补库存失败，待补偿: orderNo={}", orderNo, e);
        }
        log.info("订单已取消: orderNo={}, userId={}", orderNo, userId);
    }

    // ==================== 内部接口 ====================

    /** 支付侧取订单信息（校验归属与可支付性由支付服务负责） */
    public OrderForPayDTO getForPay(String orderNo) {
        Order order = requireByNo(orderNo);
        OrderForPayDTO dto = new OrderForPayDTO();
        dto.setOrderNo(order.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setStatus(order.getStatus());
        dto.setPayAmount(order.getPayAmount());
        return dto;
    }

    /**
     * 标记订单已支付（支付回调触发；幂等）
     * <p>
     * 幂等重试时补做库存确认（库存侧按流水幂等），保证「订单已支付 → 库存已确认」最终收敛。
     */
    public void markPaid(String orderNo) {
        Order order = requireByNo(orderNo);
        if (order.getStatus() == OrderStatus.PAID) {
            confirmStockQuietly(orderNo);
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAY) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL);
        }
        transactStatus(order, OrderStatus.PAID, OrderStatus.OPERATOR_SYSTEM, 0L, "支付成功", false);

        // 通知库存确认扣减（异常上抛，支付侧记录待补偿）
        ResultHelper.unwrap(stockClient.confirm(buildStockOp(orderNo, listItems(orderNo))));
        log.info("订单支付完成: orderNo={}", orderNo);
    }

    // ==================== 内部工具 ====================

    /**
     * 状态流转（本地事务）：CAS 更新主单 + 写流转日志；并发流转失败抛 5002
     */
    private void transactStatus(Order order, int toStatus, int operatorType, long operatorId,
                                String remark, boolean setCloseTime) {
        transactionTemplate.executeWithoutResult(status -> {
            int fromStatus = order.getStatus();
            LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<Order>()
                    .eq(Order::getOrderNo, order.getOrderNo())
                    .eq(Order::getStatus, fromStatus)
                    .set(Order::getStatus, toStatus);
            if (toStatus == OrderStatus.PAID) {
                wrapper.set(Order::getPayTime, LocalDateTime.now());
            }
            if (setCloseTime) {
                wrapper.set(Order::getCloseTime, LocalDateTime.now());
            }
            int rows = orderMapper.update(null, wrapper);
            if (rows == 0) {
                throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL);
            }
            insertLog(order.getOrderNo(), fromStatus, toStatus, operatorType, operatorId, remark);
        });
        order.setStatus(toStatus);
    }

    /** 锁库存失败：撤销订单 + 尽力回补（两个动作都尽力执行并留痕） */
    private void handleLockFailure(Order order, StockOpRequest stockOp) {
        try {
            transactStatus(order, OrderStatus.CANCELLED, OrderStatus.OPERATOR_SYSTEM,
                    0L, "锁定库存失败，自动取消", true);
        } catch (Exception e) {
            log.error("锁定失败后撤销订单异常: orderNo={}", order.getOrderNo(), e);
        }
        try {
            ResultHelper.unwrap(stockClient.restore(stockOp));
        } catch (Exception e) {
            log.error("锁定失败后回补库存异常: orderNo={}", order.getOrderNo(), e);
        }
    }

    private void confirmStockQuietly(String orderNo) {
        try {
            ResultHelper.unwrap(stockClient.confirm(buildStockOp(orderNo, listItems(orderNo))));
        } catch (Exception e) {
            log.warn("订单已支付但库存确认重试失败: orderNo={}", orderNo, e);
        }
    }

    private Order requireByNo(String orderNo) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private Order requireOwned(Long userId, String orderNo) {
        Order order = requireByNo(orderNo);
        if (!order.getUserId().equals(userId)) {
            // 不暴露他人订单是否存在
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private AddressDTO fetchAddress(Long userId, Long addressId) {
        AddressDTO address;
        try {
            address = ResultHelper.unwrap(userClient.getAddress(addressId));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用用户服务失败", e);
            throw new BizException("用户服务暂不可用，请稍后重试");
        }
        if (address == null || !userId.equals(address.getUserId())) {
            throw new BizException(ResultCode.ADDRESS_NOT_FOUND);
        }
        return address;
    }

    private Map<Long, SkuInfoDTO> fetchSkuMap(Collection<Long> skuIds) {
        List<SkuInfoDTO> list;
        try {
            list = ResultHelper.unwrap(productClient.batchGetSkuInfo(new ArrayList<>(skuIds)));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用商品服务失败", e);
            throw new BizException("商品服务暂不可用，请稍后重试");
        }
        Map<Long, SkuInfoDTO> map = new HashMap<>();
        if (list != null) {
            list.forEach(sku -> map.put(sku.getSkuId(), sku));
        }
        return map;
    }

    private StockOpRequest buildStockOp(String orderNo, LinkedHashMap<Long, Integer> merged) {
        StockOpRequest request = new StockOpRequest();
        request.setOrderNo(orderNo);
        request.setItems(merged.entrySet().stream().map(entry -> {
            StockOpRequest.Item item = new StockOpRequest.Item();
            item.setSkuId(entry.getKey());
            item.setQuantity(entry.getValue());
            return item;
        }).toList());
        return request;
    }

    private StockOpRequest buildStockOp(String orderNo, List<OrderItem> items) {
        StockOpRequest request = new StockOpRequest();
        request.setOrderNo(orderNo);
        request.setItems(items.stream().map(orderItem -> {
            StockOpRequest.Item item = new StockOpRequest.Item();
            item.setSkuId(orderItem.getSkuId());
            item.setQuantity(orderItem.getQuantity());
            return item;
        }).toList());
        return request;
    }

    private List<OrderItem> listItems(String orderNo) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderNo, orderNo)
                .orderByAsc(OrderItem::getId));
    }

    private Map<String, List<OrderItem>> loadItemsMap(List<String> orderNos) {
        if (orderNos.isEmpty()) {
            return Map.of();
        }
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderNo, orderNos)
                        .orderByAsc(OrderItem::getId))
                .stream().collect(Collectors.groupingBy(OrderItem::getOrderNo));
    }

    private void insertLog(String orderNo, int fromStatus, int toStatus,
                           int operatorType, long operatorId, String remark) {
        OrderLog orderLog = new OrderLog();
        orderLog.setOrderNo(orderNo);
        orderLog.setFromStatus(fromStatus);
        orderLog.setToStatus(toStatus);
        orderLog.setOperatorType(operatorType);
        orderLog.setOperatorId(operatorId);
        orderLog.setRemark(remark);
        orderLogMapper.insert(orderLog);
    }

    private String generateOrderNo() {
        // ES + 14 位时间 + 6 位随机，共 22 位（< 32，DB 唯一索引兜底）
        return "ES" + ORDER_NO_TIME.format(LocalDateTime.now())
                + ThreadLocalRandom.current().nextInt(100000, 1000000);
    }

    private String joinAddress(AddressDTO address) {
        return nvl(address.getProvince()) + nvl(address.getCity())
                + nvl(address.getDistrict()) + nvl(address.getDetailAddress());
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(DATETIME_FORMATTER);
    }

    private OrderVO toListVO(Order order, List<OrderItem> items) {
        OrderVO vo = new OrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusText(OrderStatus.text(order.getStatus()));
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setCreateTime(formatDateTime(order.getCreateTime()));
        vo.setItems(items.stream().map(this::toItemVO).toList());
        return vo;
    }

    private OrderItemVO toItemVO(OrderItem item) {
        OrderItemVO vo = new OrderItemVO();
        vo.setSkuId(item.getSkuId());
        vo.setSpuId(item.getSpuId());
        vo.setName(item.getSpuName());
        vo.setSpecText(item.getSkuName());
        vo.setImage(item.getSkuImage());
        vo.setPrice(item.getPrice());
        vo.setQuantity(item.getQuantity());
        vo.setTotalAmount(item.getTotalAmount());
        return vo;
    }
}
