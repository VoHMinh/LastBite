package com.LastBite.modules.promotion.dto.response;

import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.promotion.enums.VoucherCampaignOwnerType;
import com.LastBite.modules.promotion.enums.VoucherCampaignStatus;
import com.LastBite.modules.promotion.enums.VoucherDiscountType;
import com.LastBite.modules.promotion.enums.VoucherFundingSource;
import com.LastBite.modules.store.enums.StoreCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class VoucherCampaignResponse {
    private UUID id;
    private VoucherCampaignOwnerType ownerType;
    private UUID storeId;
    private String storeName;
    private UUID bagId;
    private String bagName;
    private StoreCategory category;
    private BagType bagType;
    private DietType dietType;
    private VoucherCampaignStatus status;
    private String name;
    private String description;
    private VoucherDiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minOrderAmount;
    private VoucherFundingSource fundingSource;
    private int platformFundingBps;
    private int merchantFundingBps;
    private Instant startsAt;
    private Instant endsAt;
    private BigDecimal budgetLimitAmount;
    private BigDecimal reservedBudgetAmount;
    private BigDecimal redeemedBudgetAmount;
    private Integer totalUsageLimit;
    private int perUserLimit;
    private int reservedCount;
    private int redeemedCount;
    private boolean firstOrderOnly;
    private UUID createdByUserId;
    private UUID approvedByUserId;
    private Instant approvedAt;
    private Instant publishedAt;
    private Instant endedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
