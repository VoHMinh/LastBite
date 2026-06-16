package com.LastBite.modules.payment.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.payment.enums.PaymentProvider;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "payment_webhooks", indexes = {
        @Index(name = "idx_payment_webhooks_provider_order_code", columnList = "provider_order_code"),
        @Index(name = "idx_payment_webhooks_processed", columnList = "processed")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentWebhook extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentProvider provider;

    @Column(name = "event_key", nullable = false, unique = true, length = 220)
    private String eventKey;

    @Column(name = "provider_order_code")
    private Long providerOrderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(length = 255)
    private String signature;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "valid_signature", nullable = false)
    @Builder.Default
    private boolean validSignature = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean processed = false;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;
}
