package com.eshop.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eshop.common.dto.AddressDTO;
import com.eshop.common.exception.BizException;
import com.eshop.common.result.ResultCode;
import com.eshop.user.dto.AddressRequest;
import com.eshop.user.entity.UserAddress;
import com.eshop.user.mapper.UserAddressMapper;
import com.eshop.user.vo.AddressVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 收货地址服务
 * <p>
 * 规则：默认地址互斥（设为默认时清除其他）；用户名下首个地址自动设为默认。
 */
@Service
@RequiredArgsConstructor
public class AddressService {

    private final UserAddressMapper addressMapper;

    /** 地址列表（默认地址置顶，其次按创建倒序） */
    public List<AddressVO> list(Long userId) {
        return addressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getUserId, userId)
                        .orderByDesc(UserAddress::getIsDefault)
                        .orderByDesc(UserAddress::getId))
                .stream().map(this::toVO).toList();
    }

    /** 新增地址，返回地址 ID */
    public Long create(Long userId, AddressRequest request) {
        Long count = addressMapper.selectCount(
                new LambdaQueryWrapper<UserAddress>().eq(UserAddress::getUserId, userId));
        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault()) || count == null || count == 0;
        if (makeDefault) {
            clearDefault(userId);
        }
        UserAddress address = new UserAddress();
        address.setUserId(userId);
        applyRequest(address, request);
        address.setIsDefault(makeDefault ? 1 : 0);
        addressMapper.insert(address);
        return address.getId();
    }

    /** 修改地址（归属校验） */
    public void update(Long userId, Long id, AddressRequest request) {
        UserAddress address = requireOwned(userId, id);
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefault(userId);
            address.setIsDefault(1);
        }
        applyRequest(address, request);
        addressMapper.updateById(address);
    }

    /** 删除地址（逻辑删除；不存在或非本人抛 1004） */
    public void delete(Long userId, Long id) {
        int rows = addressMapper.delete(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, id)
                .eq(UserAddress::getUserId, userId));
        if (rows == 0) {
            throw new BizException(ResultCode.ADDRESS_NOT_FOUND);
        }
    }

    /** 内部接口：按 ID 取地址（含 userId，调用方自行校验归属） */
    public AddressDTO getInternal(Long id) {
        UserAddress address = addressMapper.selectById(id);
        if (address == null) {
            throw new BizException(ResultCode.ADDRESS_NOT_FOUND);
        }
        AddressDTO dto = new AddressDTO();
        dto.setId(address.getId());
        dto.setUserId(address.getUserId());
        dto.setReceiverName(address.getReceiverName());
        dto.setReceiverPhone(address.getReceiverPhone());
        dto.setProvince(address.getProvince());
        dto.setCity(address.getCity());
        dto.setDistrict(address.getDistrict());
        dto.setDetailAddress(address.getDetailAddress());
        dto.setIsDefault(address.getIsDefault());
        return dto;
    }

    // ==================== 内部工具 ====================

    private UserAddress requireOwned(Long userId, Long id) {
        UserAddress address = addressMapper.selectById(id);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BizException(ResultCode.ADDRESS_NOT_FOUND);
        }
        return address;
    }

    /** 清除该用户的默认标记（默认地址互斥） */
    private void clearDefault(Long userId) {
        UserAddress update = new UserAddress();
        update.setIsDefault(0);
        addressMapper.update(update, new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, 1));
    }

    private void applyRequest(UserAddress address, AddressRequest request) {
        address.setReceiverName(request.getReceiverName());
        address.setReceiverPhone(request.getReceiverPhone());
        address.setProvince(request.getProvince() == null ? "" : request.getProvince());
        address.setCity(request.getCity() == null ? "" : request.getCity());
        address.setDistrict(request.getDistrict() == null ? "" : request.getDistrict());
        address.setDetailAddress(request.getDetailAddress());
    }

    private AddressVO toVO(UserAddress address) {
        AddressVO vo = new AddressVO();
        vo.setId(address.getId());
        vo.setReceiverName(address.getReceiverName());
        vo.setReceiverPhone(address.getReceiverPhone());
        vo.setProvince(address.getProvince());
        vo.setCity(address.getCity());
        vo.setDistrict(address.getDistrict());
        vo.setDetailAddress(address.getDetailAddress());
        vo.setIsDefault(address.getIsDefault());
        return vo;
    }
}
