package com.LastBite.modules.promotion.dto.response;

import com.LastBite.modules.promotion.enums.VoucherFundingSource;
import com.LastBite.modules.promotion.enums.VoucherRedemptionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class VoucherRedemptionResponse {
    private UUID id;
    private UUID orderId;
    private String orderNumber;
    private UUID userId;
    private String userName;
    private UUID campaignId;
    private String campaignName;
    private UUID voucherCodeId;
    private String voucherCode;
    private UUID userVoucherId;
    private VoucherFundingSource fundingSource;
    private VoucherRedemptionStatus status;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal platformFundedAmount;
    private BigDecimal merchantFundedAmount;
    private Instant reservedAt;
    private Instant reservedUntil;
    private Instant redeemedAt;
    private Instant releasedAt;
    private Instant reissuedAt;
    private String releaseReason;
    private Instant createdAt;
    private Instant updatedAt;
}
