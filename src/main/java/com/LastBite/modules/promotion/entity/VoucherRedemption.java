package com.LastBite.modules.promotion.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.promotion.enums.VoucherDiscountType;
import com.LastBite.modules.promotion.enums.VoucherFundingSource;
import com.LastBite.modules.promotion.enums.VoucherRedemptionStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "voucher_redemptions", indexes = {
        @Index(name = "idx_voucher_redemptions_user_status", columnList = "user_id,status"),
        @Index(name = "idx_voucher_redemptions_campaign_status", columnList = "campaign_id,status"),
        @Index(name = "idx_voucher_redemptions_order", columnList = "order_id", unique = true)
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherRedemption extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private VoucherCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_code_id")
    private VoucherCode code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_voucher_id")
    private UserVoucher userVoucher;

    @Column(name = "code_snapshot", length = 80)
    private String codeSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 30)
    private VoucherDiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "funding_source", nullable = false, length = 30)
    private VoucherFundingSource fundingSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private VoucherRedemptionStatus status = VoucherRedemptionStatus.RESERVED;

    @Column(name = "subtotal_amount", nullable = false, precision = 10, scale = 0)
    private BigDecimal subtotalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 0)
    private BigDecimal discountAmount;

    @Column(name = "platform_funded_amount", nullable = false, precision = 10, scale = 0)
    @Builder.Default
    private BigDecimal platformFundedAmount = BigDecimal.ZERO;

    @Column(name = "merchant_funded_amount", nullable = false, precision = 10, scale = 0)
    @Builder.Default
    private BigDecimal merchantFundedAmount = BigDecimal.ZERO;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    @Column(name = "reserved_until")
    private Instant reservedUntil;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "reissued_at")
    private Instant reissuedAt;

    @Column(name = "release_reason", length = 500)
    private String releaseReason;
}
