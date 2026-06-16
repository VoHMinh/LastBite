package com.LastBite.modules.refund.dto.request;

import com.LastBite.modules.refund.enums.RefundReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRefundRequest {
    @NotNull
    private RefundReason reason;

    @Size(max = 2000)
    private String description;
}
