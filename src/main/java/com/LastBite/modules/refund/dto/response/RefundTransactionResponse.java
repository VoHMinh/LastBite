package com.LastBite.modules.refund.dto.response;

import com.LastBite.modules.payment.enums.PaymentProvider;
import com.LastBite.modules.refund.enums.RefundTransactionMethod;
import com.LastBite.modules.refund.enums.RefundTransactionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class RefundTransactionResponse {
    private UUID id;
    private BigDecimal amount;
    private RefundTransactionStatus status;
    private RefundTransactionMethod method;
    private PaymentProvider provider;
    private String providerReference;
    private String failureReason;
    private int attemptCount;
    private Instant processedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
