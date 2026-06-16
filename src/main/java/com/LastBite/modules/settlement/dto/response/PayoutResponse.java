package com.LastBite.modules.settlement.dto.response;

import com.LastBite.modules.payment.enums.PaymentProvider;
import com.LastBite.modules.settlement.enums.PayoutStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PayoutResponse {
    private UUID id;
    private UUID settlementId;
    private UUID storeId;
    private UUID bankAccountId;
    private PaymentProvider provider;
    private String idempotencyKey;
    private BigDecimal amount;
    private PayoutStatus status;
    private String providerPayoutId;
    private String providerTransactionId;
    private String failureReason;
    private Instant requestedAt;
    private Instant paidAt;
    private Instant createdAt;
    private Instant updatedAt;
}
