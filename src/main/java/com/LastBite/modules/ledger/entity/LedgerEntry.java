package com.LastBite.modules.ledger.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.ledger.enums.LedgerEntryDirection;
import com.LastBite.modules.ledger.enums.LedgerEntryType;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.payment.entity.Payment;
import com.LastBite.modules.refund.entity.RefundRequest;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries", indexes = {
        @Index(name = "idx_ledger_entries_account_id", columnList = "account_id"),
        @Index(name = "idx_ledger_entries_order_id", columnList = "order_id"),
        @Index(name = "idx_ledger_entries_settlement", columnList = "settlement_id"),
        @Index(name = "idx_ledger_entries_available", columnList = "available_at")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private LedgerAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_request_id")
    private RefundRequest refundRequest;

    @Column(name = "settlement_id")
    private UUID settlementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 50)
    private LedgerEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_direction", nullable = false, length = 10)
    private LedgerEntryDirection direction;

    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "available_at")
    private Instant availableAt;

    @Column(length = 1000)
    private String description;
}
