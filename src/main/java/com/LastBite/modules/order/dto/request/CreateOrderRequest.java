package com.LastBite.modules.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateOrderRequest {

    @NotNull(message = "Túi không được để trống")
    private UUID bagId;

    @Min(value = 1, message = "Mỗi đơn phải có ít nhất 1 túi")
    @Max(value = 3, message = "Mỗi đơn chỉ được đặt tối đa 3 túi")
    private int quantity = 1;

    @NotBlank(message = "Idempotency key không được để trống")
    @Size(max = 100, message = "Idempotency key tối đa 100 ký tự")
    private String idempotencyKey;

    @Size(max = 80, message = "Voucher code toi da 80 ky tu")
    private String voucherCode;

    private UUID userVoucherId;
}
