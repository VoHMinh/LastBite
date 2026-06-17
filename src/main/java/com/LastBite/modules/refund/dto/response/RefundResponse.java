package com.LastBite.modules.refund.dto.response;

import com.LastBite.modules.refund.enums.RefundReason;
import com.LastBite.modules.refund.enums.RefundStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RefundResponse {
    private UUID id;
    private UUID orderId;
    private UUID paymentId;
    private UUID requestedByUserId;
    private RefundReason reason;
    private RefundStatus status;
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private UUID reviewedByUserId;
    private Instant reviewedAt;
    private String description;
    private String decisionNote;
    private boolean autoCreated;
    private String refundBankCode;
    private String refundBankName;
    private String refundAccountHolderName;
    private String maskedRefundAccountNumber;
    private boolean destinationRequired;
    private List<RefundTransactionResponse> transactions;
    private Instant createdAt;
    private Instant updatedAt;
}
