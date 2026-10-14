package com.eshop.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 收货地址新增 / 修改请求（POST/PUT /api/user/addresses）
 */
@Data
public class AddressRequest {

    @NotBlank(message = "收货人姓名不能为空")
    @Size(max = 32, message = "收货人姓名过长")
    private String receiverName;

    @NotBlank(message = "收货人手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String receiverPhone;

    @Size(max = 32, message = "省份过长")
    private String province;

    @Size(max = 32, message = "城市过长")
    private String city;

    @Size(max = 32, message = "区县过长")
    private String district;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 255, message = "详细地址过长")
    private String detailAddress;

    /** 是否设为默认地址（首个地址自动为默认） */
    private Boolean isDefault;
}
