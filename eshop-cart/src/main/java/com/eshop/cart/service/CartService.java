package com.eshop.cart.service;

import com.eshop.cart.client.ProductSkuClient;
import com.eshop.cart.dto.CartAddRequest;
import com.eshop.cart.dto.CartCheckedRequest;
import com.eshop.cart.dto.CartQuantityRequest;
import com.eshop.cart.model.CartItemState;
import com.eshop.cart.vo.CartItemVO;
import com.eshop.common.dto.SkuInfoDTO;
import com.eshop.common.exception.BizException;
import com.eshop.common.result.Result;
import com.eshop.common.result.ResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 购物车服务（Redis Hash 存储，不进 MySQL）
 * <p>
 * 结构：key = cart:{userId}，field = skuId，value = {@link CartItemState} 的 JSON；
 * 展示信息（名称/图/价格/规格文案）由 OpenFeign 实时从商品服务补充；
 * 每次写入续期 30 天，不活跃自动过期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private static final String CART_KEY_PREFIX = "cart:";
    private static final int MAX_QUANTITY = 99;
    private static final Duration CART_TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductSkuClient productSkuClient;

    /**
     * 购物车列表：按加购时间倒序；商品已删除/下架的条目跳过（保留 Redis 数据，避免误删用户操作）
     */
    public List<CartItemVO> list(Long userId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(cartKey(userId));
        if (entries.isEmpty()) {
            return List.of();
        }

        List<Map.Entry<Long, CartItemState>> items = entries.entrySet().stream()
                .map(entry -> {
                    CartItemState state = parseState(entry.getValue());
                    return state == null ? null
                            : Map.entry(Long.valueOf(String.valueOf(entry.getKey())), state);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                                (Map.Entry<Long, CartItemState> e) ->
                                        e.getValue().getAddTime() == null ? 0L : e.getValue().getAddTime())
                        .reversed())
                .toList();

        Map<Long, SkuInfoDTO> infoMap = querySkuInfos(items.stream()
                        .map(Map.Entry::getKey).toList()).stream()
                .collect(Collectors.toMap(SkuInfoDTO::getSkuId, Function.identity(), (a, b) -> a));

        List<CartItemVO> result = new ArrayList<>();
        for (Map.Entry<Long, CartItemState> item : items) {
            SkuInfoDTO info = infoMap.get(item.getKey());
            if (info == null) {
                continue;
            }
            CartItemVO vo = new CartItemVO();
            vo.setSkuId(info.getSkuId());
            vo.setSpuId(info.getSpuId());
            vo.setName(info.getSpuName());
            vo.setSpecText(info.getSkuName());
            vo.setImage(info.getImage());
            vo.setPrice(info.getPrice());
            vo.setQuantity(item.getValue().getQuantity());
            vo.setChecked(item.getValue().getChecked());
            result.add(vo);
        }
        return result;
    }

    /**
     * 加入购物车：校验 SKU 在架（3001）；已存在则累加，上限 99；加购即勾选
     */
    public void add(Long userId, CartAddRequest request) {
        Long skuId = request.getSkuId();
        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();

        if (querySkuInfos(List.of(skuId)).isEmpty()) {
            throw new BizException(ResultCode.SKU_NOT_FOUND);
        }

        String key = cartKey(userId);
        String field = String.valueOf(skuId);
        CartItemState state = parseState(getRaw(key, field));
        if (state == null) {
            state = new CartItemState(0, true, System.currentTimeMillis());
        }
        int current = state.getQuantity() == null ? 0 : state.getQuantity();
        state.setQuantity(Math.min(MAX_QUANTITY, current + quantity));
        state.setChecked(true);
        save(key, field, state);
        log.info("加入购物车: userId={}, skuId={}, quantity={}", userId, skuId, state.getQuantity());
    }

    /**
     * 修改数量（条目不存在抛 4001）
     */
    public void updateQuantity(Long userId, CartQuantityRequest request) {
        CartItemState state = requireState(userId, request.getSkuId());
        state.setQuantity(request.getQuantity());
        save(cartKey(userId), String.valueOf(request.getSkuId()), state);
    }

    /**
     * 勾选 / 取消勾选（条目不存在抛 4001）
     */
    public void updateChecked(Long userId, CartCheckedRequest request) {
        CartItemState state = requireState(userId, request.getSkuId());
        state.setChecked(request.getChecked());
        save(cartKey(userId), String.valueOf(request.getSkuId()), state);
    }

    /**
     * 全选 / 取消全选
     */
    public void updateCheckedAll(Long userId, boolean checked) {
        String key = cartKey(userId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return;
        }
        Map<String, String> updates = new HashMap<>();
        entries.forEach((field, value) -> {
            CartItemState state = parseState(value);
            if (state == null) {
                return;
            }
            state.setChecked(checked);
            updates.put(String.valueOf(field), serializeState(state));
        });
        if (!updates.isEmpty()) {
            redisTemplate.opsForHash().putAll(key, updates);
            redisTemplate.expire(key, CART_TTL);
        }
    }

    /**
     * 删除单条（幂等：不存在也返回成功）
     */
    public void remove(Long userId, Long skuId) {
        redisTemplate.opsForHash().delete(cartKey(userId), String.valueOf(skuId));
    }

    /**
     * 批量删除（幂等）
     */
    public void batchRemove(Long userId, List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return;
        }
        Object[] fields = skuIds.stream().map(String::valueOf).toArray();
        redisTemplate.opsForHash().delete(cartKey(userId), fields);
    }

    // ==================== 内部工具 ====================

    private String cartKey(Long userId) {
        return CART_KEY_PREFIX + userId;
    }

    private Object getRaw(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    private CartItemState requireState(Long userId, Long skuId) {
        CartItemState state = parseState(getRaw(cartKey(userId), String.valueOf(skuId)));
        if (state == null) {
            throw new BizException(ResultCode.CART_ITEM_NOT_FOUND);
        }
        return state;
    }

    /** 写入并续期 */
    private void save(String key, String field, CartItemState state) {
        redisTemplate.opsForHash().put(key, field, serializeState(state));
        redisTemplate.expire(key, CART_TTL);
    }

    private CartItemState parseState(Object raw) {
        if (raw == null) {
            return null;
        }
        String json = raw.toString();
        try {
            return objectMapper.readValue(json, CartItemState.class);
        } catch (JsonProcessingException e) {
            log.warn("购物车条目反序列化失败，忽略: {}", json);
            return null;
        }
    }

    private String serializeState(CartItemState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new BizException("购物车数据序列化失败");
        }
    }

    /** 调商品服务批量补充展示信息；服务不可用则抛出业务异常 */
    private List<SkuInfoDTO> querySkuInfos(List<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return List.of();
        }
        try {
            Result<List<SkuInfoDTO>> result = productSkuClient.batchGetSkuInfo(skuIds);
            if (result == null || result.getCode() != ResultCode.SUCCESS.getCode() || result.getData() == null) {
                log.warn("商品服务返回异常: {}", result == null ? "null" : result.getCode() + "/" + result.getMessage());
                return List.of();
            }
            return result.getData();
        } catch (Exception e) {
            log.error("调用商品服务失败", e);
            throw new BizException("商品服务暂不可用，请稍后重试");
        }
    }
}
