package com.LastBite.modules.refund.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.payment.entity.Payment;
import com.LastBite.modules.refund.enums.RefundReason;
import com.LastBite.modules.refund.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "refund_requests", indexes = {
        @Index(name = "idx_refund_requests_order_id", columnList = "order_id"),
        @Index(name = "idx_refund_requests_status", columnList = "status"),
        @Index(name = "idx_refund_requests_created_at", columnList = "created_at")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RefundReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RefundStatus status = RefundStatus.PENDING_REVIEW;

    @Column(name = "requested_amount", nullable = false, precision = 10, scale = 0)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 10, scale = 0)
    private BigDecimal approvedAmount;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "auto_created", nullable = false)
    @Builder.Default
    private boolean autoCreated = false;

    @Column(name = "refund_bank_code", length = 30)
    private String refundBankCode;

    @Column(name = "refund_bank_name", length = 150)
    private String refundBankName;

    @Column(name = "refund_account_holder_name", length = 255)
    private String refundAccountHolderName;

    @Column(name = "refund_account_number_encrypted", columnDefinition = "TEXT")
    private String refundAccountNumberEncrypted;

    @Column(name = "refund_account_number_last4", length = 4)
    private String refundAccountNumberLast4;
}
