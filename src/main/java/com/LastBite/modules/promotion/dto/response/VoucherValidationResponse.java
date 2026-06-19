package com.LastBite.modules.promotion.dto.response;

import com.LastBite.modules.promotion.enums.VoucherDiscountType;
import com.LastBite.modules.promotion.enums.VoucherFundingSource;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class VoucherValidationResponse {
    private UUID campaignId;
    private UUID voucherCodeId;
    private UUID userVoucherId;
    private String voucherCode;
    private String campaignName;
    private VoucherDiscountType discountType;
    private BigDecimal discountValue;
    private VoucherFundingSource fundingSource;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal platformFundedAmount;
    private BigDecimal merchantFundedAmount;
    private BigDecimal finalAmount;
}
