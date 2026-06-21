package com.LastBite.modules.order.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.bag.entity.BagDailyStock;
import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.order.enums.OrderRefundStatus;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.store.entity.Store;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_order_number", columnList = "order_number", unique = true),
        @Index(name = "idx_orders_idempotency_key", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_orders_user_id", columnList = "user_id"),
        @Index(name = "idx_orders_store_id", columnList = "store_id"),
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_reserved_until", columnList = "reserved_until"),
        @Index(name = "idx_orders_pickup_date", columnList = "pickup_date")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Order extends BaseEntity {

    @Column(name = "order_number", nullable = false, unique = true, length = 30)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bag_id", nullable = false)
    private SurpriseBag bag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_stock_id", nullable = false)
    private BagDailyStock dailyStock;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 0)
    private BigDecimal unitPrice;

    @Column(name = "platform_fee", nullable = false, precision = 10, scale = 0)
    private BigDecimal platformFee;

    @Column(nullable = false, precision = 10, scale = 0)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 0)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "final_amount", nullable = false, precision = 10, scale = 0)
    private BigDecimal finalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(name = "pickup_code", nullable = false, unique = true, length = 20)
    private String pickupCode;

    @Column(name = "pickup_date", nullable = false)
    private LocalDate pickupDate;

    @Column(name = "pickup_start_time", nullable = false)
    private LocalTime pickupStartTime;

    @Column(name = "pickup_end_time", nullable = false)
    private LocalTime pickupEndTime;

    @Column(name = "reserved_until", nullable = false)
    private Instant reservedUntil;

    @Column(name = "payment_expires_at")
    private Instant paymentExpiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 30)
    @Builder.Default
    private OrderRefundStatus refundStatus = OrderRefundStatus.NONE;

    @Column(name = "pickup_qr_token_hash", length = 128)
    private String pickupQrTokenHash;

    @Column(name = "pickup_qr_token_encrypted", columnDefinition = "TEXT")
    private String pickupQrTokenEncrypted;

    @Column(name = "pickup_code_hash", length = 128)
    private String pickupCodeHash;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;
}
