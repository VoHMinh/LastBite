package com.LastBite.modules.settlement.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.merchant.entity.MerchantBankAccount;
import com.LastBite.modules.payment.enums.PaymentProvider;
import com.LastBite.modules.settlement.enums.PayoutStatus;
import com.LastBite.modules.store.entity.Store;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "store_payouts", indexes = {
        @Index(name = "idx_store_payouts_settlement_id", columnList = "settlement_id"),
        @Index(name = "idx_store_payouts_status", columnList = "status")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class StorePayout extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    private MerchantSettlement settlement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id")
    private MerchantBankAccount bankAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PaymentProvider provider = PaymentProvider.PAYOS;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 160)
    private String idempotencyKey;

    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PayoutStatus status = PayoutStatus.PENDING;

    @Column(name = "provider_payout_id", length = 160)
    private String providerPayoutId;

    @Column(name = "provider_transaction_id", length = 160)
    private String providerTransactionId;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "paid_at")
    private Instant paidAt;
}
