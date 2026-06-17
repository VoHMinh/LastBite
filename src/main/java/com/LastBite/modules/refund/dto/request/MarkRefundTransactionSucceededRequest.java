package com.LastBite.modules.refund.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MarkRefundTransactionSucceededRequest {
    @Size(max = 160)
    private String providerReference;

    @Size(max = 1000)
    private String note;
}
