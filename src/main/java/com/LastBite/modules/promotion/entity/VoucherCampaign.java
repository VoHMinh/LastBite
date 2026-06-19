package com.LastBite.modules.promotion.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.promotion.enums.VoucherCampaignOwnerType;
import com.LastBite.modules.promotion.enums.VoucherCampaignStatus;
import com.LastBite.modules.promotion.enums.VoucherDiscountType;
import com.LastBite.modules.promotion.enums.VoucherFundingSource;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.enums.StoreCategory;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "voucher_campaigns", indexes = {
        @Index(name = "idx_voucher_campaigns_status_time", columnList = "status,starts_at,ends_at"),
        @Index(name = "idx_voucher_campaigns_store_status", columnList = "store_id,status"),
        @Index(name = "idx_voucher_campaigns_owner", columnList = "owner_type,funding_source,status")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherCampaign extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 30)
    @Builder.Default
    private VoucherCampaignOwnerType ownerType = VoucherCampaignOwnerType.PLATFORM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bag_id")
    private SurpriseBag bag;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private StoreCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "bag_type", length = 30)
    private BagType bagType;

    @Enumerated(EnumType.STRING)
    @Column(name = "diet_type", length = 30)
    private DietType dietType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private VoucherCampaignStatus status = VoucherCampaignStatus.DRAFT;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 30)
    private VoucherDiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_discount_amount", precision = 10, scale = 0)
    private BigDecimal maxDiscountAmount;

    @Column(name = "min_order_amount", nullable = false, precision = 10, scale = 0)
    @Builder.Default
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "funding_source", nullable = false, length = 30)
    @Builder.Default
    private VoucherFundingSource fundingSource = VoucherFundingSource.PLATFORM;

    @Column(name = "platform_funding_bps", nullable = false)
    @Builder.Default
    private int platformFundingBps = 10000;

    @Column(name = "merchant_funding_bps", nullable = false)
    @Builder.Default
    private int merchantFundingBps = 0;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "budget_limit_amount", precision = 14, scale = 0)
    private BigDecimal budgetLimitAmount;

    @Column(name = "reserved_budget_amount", nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal reservedBudgetAmount = BigDecimal.ZERO;

    @Column(name = "redeemed_budget_amount", nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal redeemedBudgetAmount = BigDecimal.ZERO;

    @Column(name = "total_usage_limit")
    private Integer totalUsageLimit;

    @Column(name = "per_user_limit", nullable = false)
    @Builder.Default
    private int perUserLimit = 1;

    @Column(name = "reserved_count", nullable = false)
    @Builder.Default
    private int reservedCount = 0;

    @Column(name = "redeemed_count", nullable = false)
    @Builder.Default
    private int redeemedCount = 0;

    @Column(name = "first_order_only", nullable = false)
    @Builder.Default
    private boolean firstOrderOnly = false;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "ended_at")
    private Instant endedAt;
}
