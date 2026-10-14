package com.eshop.user.vo;

import lombok.Data;

/**
 * 收货地址（GET /api/user/addresses 响应 data 元素）
 */
@Data
public class AddressVO {

    private Long id;

    private String receiverName;

    private String receiverPhone;

    private String province;

    private String city;

    private String district;

    private String detailAddress;

    /** 是否默认：1 是 / 0 否 */
    private Integer isDefault;
}
