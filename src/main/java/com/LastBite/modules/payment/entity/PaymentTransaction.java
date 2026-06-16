package com.LastBite.modules.payment.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.payment.enums.PaymentProvider;
import com.LastBite.modules.payment.enums.PaymentTransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_payment_transactions_payment_id", columnList = "payment_id")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentProvider provider;

    @Column(name = "provider_transaction_id", length = 120)
    private String providerTransactionId;

    @Column(nullable = false, precision = 10, scale = 0)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentTransactionStatus status;

    @Column(name = "provider_code", length = 30)
    private String providerCode;

    @Column(name = "provider_description", length = 500)
    private String providerDescription;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;
}
