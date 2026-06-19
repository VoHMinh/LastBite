package com.LastBite.modules.promotion.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.promotion.enums.VoucherCodeStatus;
import com.LastBite.modules.promotion.enums.VoucherCodeType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "voucher_codes", indexes = {
        @Index(name = "idx_voucher_codes_campaign_status", columnList = "campaign_id,status"),
        @Index(name = "idx_voucher_codes_type_status", columnList = "code_type,status")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherCode extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private VoucherCampaign campaign;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_type", nullable = false, length = 30)
    @Builder.Default
    private VoucherCodeType codeType = VoucherCodeType.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private VoucherCodeStatus status = VoucherCodeStatus.ACTIVE;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "reserved_count", nullable = false)
    @Builder.Default
    private int reservedCount = 0;

    @Column(name = "redeemed_count", nullable = false)
    @Builder.Default
    private int redeemedCount = 0;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;
}
