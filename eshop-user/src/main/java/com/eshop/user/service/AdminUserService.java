package com.eshop.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eshop.common.dto.UserBaseDTO;
import com.eshop.common.exception.BizException;
import com.eshop.common.result.PageResult;
import com.eshop.common.result.ResultCode;
import com.eshop.user.entity.User;
import com.eshop.user.mapper.UserMapper;
import com.eshop.user.vo.AdminUserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 管理端用户服务（P3）：用户分页 / 禁用启用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserMapper userMapper;

    /**
     * 按 userIds 批量查用户基础信息（供 order 服务回填订单用户名；查不到的 ID 不返回）
     */
    public List<UserBaseDTO> batchBase(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = userIds.stream().filter(Objects::nonNull).distinct().limit(200).toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectByIds(distinctIds).stream()
                .map(user -> {
                    UserBaseDTO dto = new UserBaseDTO();
                    dto.setUserId(user.getId());
                    dto.setUsername(user.getUsername());
                    return dto;
                })
                .toList();
    }

    /**
     * 用户分页（keyword 匹配用户名 / 昵称 / 手机号）
     */
    public PageResult<AdminUserVO> page(long page, long size, String keyword) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;

        IPage<User> result = userMapper.selectPage(new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<User>()
                        .and(kw != null, w -> w.like(User::getUsername, kw)
                                .or().like(User::getNickname, kw)
                                .or().like(User::getPhone, kw))
                        .orderByDesc(User::getId));
        return PageResult.of(result.getTotal(), safePage, safeSize,
                result.getRecords().stream().map(this::toVO).toList());
    }

    /**
     * 禁用 / 启用用户（0/1）
     */
    public void updateStatus(Long userId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ResultCode.VALIDATE_FAILED.getCode(), "status 仅支持 0（禁用）或 1（正常）");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.VALIDATE_FAILED.getCode(), "用户不存在");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getStatus, status)
                .eq(User::getId, userId));
        log.info("管理员更新用户状态: userId={}, status={}", userId, status);
    }

    private AdminUserVO toVO(User user) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(maskPhone(user.getPhone()));
        vo.setCreateTime(user.getCreateTime() == null ? "" : user.getCreateTime().format(DATETIME_FORMATTER));
        vo.setStatus(user.getStatus());
        return vo;
    }

    /** 手机号脱敏（与 C 端展示口径一致） */
    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return phone == null ? "" : phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
