package com.LastBite.modules.bag.entity;

import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.bag.enums.StockAuditAction;
import com.LastBite.modules.bag.enums.StockAuditActorType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_audit_logs", indexes = {
        @Index(name = "idx_stock_audit_logs_bag_id", columnList = "bag_id"),
        @Index(name = "idx_stock_audit_logs_daily_stock_id", columnList = "daily_stock_id"),
        @Index(name = "idx_stock_audit_logs_order_id", columnList = "order_id"),
        @Index(name = "idx_stock_audit_logs_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAuditLog {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bag_id", nullable = false)
    private SurpriseBag bag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_stock_id")
    private BagDailyStock dailyStock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 30)
    @Builder.Default
    private StockAuditActorType actorType = StockAuditActorType.SYSTEM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StockAuditAction action;

    @Column(nullable = false)
    private int delta;

    @Column(name = "quantity_before", nullable = false)
    private int quantityBefore;

    @Column(name = "quantity_after", nullable = false)
    private int quantityAfter;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "order_id")
    private UUID orderId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
