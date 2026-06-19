package com.LastBite.modules.promotion.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClaimVoucherRequest {

    @NotBlank(message = "Ma voucher khong duoc de trong")
    @Size(max = 80, message = "Ma voucher toi da 80 ky tu")
    private String voucherCode;
}
