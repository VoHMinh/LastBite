package com.LastBite.modules.promotion.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ValidateVoucherRequest {

    @NotNull(message = "Bag id khong duoc de trong")
    private UUID bagId;

    @Min(value = 1, message = "So luong toi thieu la 1")
    @Max(value = 3, message = "So luong toi da la 3")
    private int quantity = 1;

    @Size(max = 80, message = "Ma voucher toi da 80 ky tu")
    private String voucherCode;

    private UUID userVoucherId;
}
