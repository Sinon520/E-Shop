package com.eshop.user.controller;

import com.eshop.common.result.PageResult;
import com.eshop.common.result.Result;
import com.eshop.user.dto.UserStatusRequest;
import com.eshop.user.service.AdminUserService;
import com.eshop.user.vo.AdminUserVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户接口（管理后台，P3；网关校验 role=admin）
 */
@Tag(name = "管理端-用户", description = "用户列表 / 禁用启用")
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 用户分页 */
    @GetMapping("/page")
    public Result<PageResult<AdminUserVO>> page(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) String keyword) {
        return Result.success(adminUserService.page(page, size, keyword));
    }

    /** 禁用 / 启用用户 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody @Valid UserStatusRequest request) {
        adminUserService.updateStatus(id, request.getStatus());
        return Result.success();
    }
}
