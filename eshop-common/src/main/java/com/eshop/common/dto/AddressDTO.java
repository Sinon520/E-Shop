package com.eshop.common.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 收货地址信息
 * <p>
 * 传输方向：eshop-user 内部接口（GET /internal/addresses/{id}）→ eshop-order（下单时取快照）
 */
@Data
public class AddressDTO implements Serializable {

    private Long id;

    /** 归属用户 ID（调用方需校验与当前用户一致） */
    private Long userId;

    private String receiverName;

    private String receiverPhone;

    private String province;

    private String city;

    private String district;

    private String detailAddress;

    /** 是否默认地址：1 是 / 0 否 */
    private Integer isDefault;
}
