package com.LastBite.modules.promotion.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.promotion.enums.UserVoucherStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "user_vouchers", indexes = {
        @Index(name = "idx_user_vouchers_user_status", columnList = "user_id,status"),
        @Index(name = "idx_user_vouchers_campaign_status", columnList = "campaign_id,status")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserVoucher extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private VoucherCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_code_id")
    private VoucherCode code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private UserVoucherStatus status = UserVoucherStatus.CLAIMED;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserved_order_id")
    private Order reservedOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redeemed_order_id")
    private Order redeemedOrder;

    @Column(name = "reserved_until")
    private Instant reservedUntil;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;
}
