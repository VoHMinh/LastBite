package com.LastBite.modules.promotion.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class VoucherCampaignAnalyticsResponse {
    private UUID campaignId;
    private long redeemedOrders;
    private BigDecimal totalDiscountAmount;
    private BigDecimal platformFundedAmount;
    private BigDecimal merchantFundedAmount;
}
