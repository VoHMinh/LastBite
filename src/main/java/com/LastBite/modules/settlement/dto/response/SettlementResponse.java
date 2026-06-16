package com.LastBite.modules.settlement.dto.response;

import com.LastBite.modules.settlement.enums.MerchantSettlementStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class SettlementResponse {
    private UUID id;
    private UUID businessProfileId;
    private UUID storeId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal grossAmount;
    private BigDecimal commissionAmount;
    private BigDecimal refundAmount;
    private BigDecimal netAmount;
    private MerchantSettlementStatus status;
    private UUID approvedByUserId;
    private Instant approvedAt;
    private Instant paidAt;
    private Instant createdAt;
    private Instant updatedAt;
}
