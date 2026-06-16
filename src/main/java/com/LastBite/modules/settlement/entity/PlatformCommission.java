package com.LastBite.modules.settlement.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.payment.entity.Payment;
import com.LastBite.modules.settlement.enums.CommissionStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "platform_commissions")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformCommission extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "gross_amount", nullable = false, precision = 10, scale = 0)
    private BigDecimal grossAmount;

    @Column(name = "platform_fee_amount", nullable = false, precision = 10, scale = 0)
    private BigDecimal platformFeeAmount;

    @Column(name = "merchant_net_amount", nullable = false, precision = 10, scale = 0)
    private BigDecimal merchantNetAmount;

    @Column(name = "rate_bps", nullable = false)
    @Builder.Default
    private int rateBps = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private CommissionStatus status = CommissionStatus.PENDING;
}
