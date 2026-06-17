package com.LastBite.modules.payment.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.payment.enums.GatewayRequestStatus;
import com.LastBite.modules.payment.enums.GatewayRequestType;
import com.LastBite.modules.payment.enums.PaymentProvider;
import com.LastBite.modules.refund.entity.RefundTransaction;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "payment_gateway_requests",
        uniqueConstraints = @UniqueConstraint(name = "uq_payment_gateway_requests_provider_idem",
                columnNames = {"provider", "idempotency_key"}))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentGatewayRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_transaction_id")
    private RefundTransaction refundTransaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 40)
    private GatewayRequestType requestType;

    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GatewayRequestStatus status;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "provider_reference", length = 160)
    private String providerReference;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;
}
