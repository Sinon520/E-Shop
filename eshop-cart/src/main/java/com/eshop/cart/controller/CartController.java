package com.eshop.cart.controller;

import com.eshop.cart.dto.CartAddRequest;
import com.eshop.cart.dto.CartBatchRemoveRequest;
import com.eshop.cart.dto.CartCheckedAllRequest;
import com.eshop.cart.dto.CartCheckedRequest;
import com.eshop.cart.dto.CartQuantityRequest;
import com.eshop.cart.service.CartService;
import com.eshop.cart.vo.CartItemVO;
import com.eshop.common.constant.AuthConstants;
import com.eshop.common.result.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 购物车接口（全部需登录；用户 ID 由网关校验 JWT 后经 X-User-Id 透传，服务端按用户隔离数据）
 */
@Tag(name = "购物车", description = "Redis 购物车（需登录）")
@RestController
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /** 购物车列表 */
    @GetMapping("/list")
    public Result<List<CartItemVO>> list(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId) {
        return Result.success(cartService.list(userId));
    }

    /** 加入购物车 */
    @PostMapping("/add")
    public Result<Void> add(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                            @Valid @RequestBody CartAddRequest request) {
        cartService.add(userId, request);
        return Result.success();
    }

    /** 修改数量 */
    @PutMapping("/quantity")
    public Result<Void> updateQuantity(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                       @Valid @RequestBody CartQuantityRequest request) {
        cartService.updateQuantity(userId, request);
        return Result.success();
    }

    /** 勾选 / 取消勾选 */
    @PutMapping("/checked")
    public Result<Void> updateChecked(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                      @Valid @RequestBody CartCheckedRequest request) {
        cartService.updateChecked(userId, request);
        return Result.success();
    }

    /** 全选 / 取消全选 */
    @PutMapping("/checked-all")
    public Result<Void> updateCheckedAll(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                         @Valid @RequestBody CartCheckedAllRequest request) {
        cartService.updateCheckedAll(userId, request.getChecked());
        return Result.success();
    }

    /** 删除单条（路径约束为纯数字，避免把 /doc.html 等单段路径误匹配进来） */
    @DeleteMapping("/{skuId:\\d+}")
    public Result<Void> remove(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                               @PathVariable Long skuId) {
        cartService.remove(userId, skuId);
        return Result.success();
    }

    /** 批量删除（原型「删除选中」= 前端收集已勾选 skuId 后调用） */
    @PostMapping("/batch-remove")
    public Result<Void> batchRemove(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                    @Valid @RequestBody CartBatchRemoveRequest request) {
        cartService.batchRemove(userId, request.getSkuIds());
        return Result.success();
    }
}
