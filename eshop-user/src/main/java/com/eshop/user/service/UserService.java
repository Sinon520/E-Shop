package com.eshop.user.service;

import com.eshop.common.exception.BizException;
import com.eshop.common.result.ResultCode;
import com.eshop.user.entity.User;
import com.eshop.user.mapper.UserMapper;
import com.eshop.user.vo.ProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * 用户资料服务
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserMapper userMapper;

    /**
     * 当前用户信息（手机号脱敏）
     */
    public ProfileVO getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }

        ProfileVO vo = new ProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(maskPhone(user.getPhone()));
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setCreateTime(user.getCreateTime() == null ? "" : user.getCreateTime().format(DATETIME_FORMATTER));
        return vo;
    }

    /**
     * 手机号脱敏：138****0000；空值返回空串
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        if (phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
