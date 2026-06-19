package com.LastBite.modules.promotion.dto.response;

import com.LastBite.modules.promotion.enums.UserVoucherStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class UserVoucherResponse {
    private UUID id;
    private UUID campaignId;
    private String campaignName;
    private UUID voucherCodeId;
    private String voucherCode;
    private UserVoucherStatus status;
    private Instant expiresAt;
    private UUID reservedOrderId;
    private UUID redeemedOrderId;
    private Instant reservedUntil;
    private Instant redeemedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
