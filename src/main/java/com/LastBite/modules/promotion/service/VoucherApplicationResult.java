package com.LastBite.modules.promotion.service;

import com.LastBite.modules.promotion.enums.VoucherFundingSource;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class VoucherApplicationResult {
    private UUID campaignId;
    private UUID voucherCodeId;
    private UUID userVoucherId;
    private String voucherCode;
    private String campaignName;
    private VoucherFundingSource fundingSource;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal platformFundedAmount;
    private BigDecimal merchantFundedAmount;
    private BigDecimal finalAmount;

    public static VoucherApplicationResult none(BigDecimal subtotal) {
        return VoucherApplicationResult.builder()
                .subtotalAmount(subtotal)
                .discountAmount(BigDecimal.ZERO)
                .platformFundedAmount(BigDecimal.ZERO)
                .merchantFundedAmount(BigDecimal.ZERO)
                .finalAmount(subtotal)
                .build();
    }
}
