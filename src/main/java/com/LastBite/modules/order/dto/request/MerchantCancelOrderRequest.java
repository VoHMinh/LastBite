package com.LastBite.modules.order.dto.request;

import com.LastBite.modules.refund.enums.RefundReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantCancelOrderRequest {
    @NotNull
    private RefundReason reason;

    @Size(max = 1000)
    private String note;
}
