package com.LastBite.modules.refund.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MarkRefundTransactionFailedRequest {
    @NotBlank
    @Size(max = 1000)
    private String failureReason;
}
