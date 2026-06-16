package com.LastBite.modules.refund.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReviewRefundRequest {
    @NotNull
    private Boolean approve;

    @DecimalMin("0")
    private BigDecimal approvedAmount;

    @Size(max = 1000)
    private String decisionNote;
}
