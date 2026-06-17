package com.LastBite.modules.refund.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.payment.enums.PaymentProvider;
import com.LastBite.modules.refund.enums.RefundTransactionMethod;
import com.LastBite.modules.refund.enums.RefundTransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "refund_transactions", indexes = {
        @Index(name = "idx_refund_transactions_request_id", columnList = "refund_request_id")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RefundTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_request_id", nullable = false)
    private RefundRequest refundRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PaymentProvider provider = PaymentProvider.PAYOS;

    @Column(name = "provider_reference", length = 160)
    private String providerReference;

    @Column(nullable = false, precision = 10, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RefundTransactionStatus status = RefundTransactionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RefundTransactionMethod method = RefundTransactionMethod.MANUAL_BANK_TRANSFER;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "idempotency_key", length = 160)
    private String idempotencyKey;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;
}
