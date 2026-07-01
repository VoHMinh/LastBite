package com.LastBite.modules.order.dto.response;

import com.LastBite.modules.order.enums.OrderRefundStatus;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.payment.enums.PaymentStatus;
import com.LastBite.modules.review.dto.response.ReviewResponse;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
public class MerchantOrderResponse {
    private UUID id;
    private String orderNumber;
    private UUID customerId;
    private String customerName;
    private String customerPhone;
    private UUID storeId;
    private String storeName;
    private UUID bagId;
    private String bagName;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal finalAmount;
    private OrderStatus status;
    private OrderRefundStatus refundStatus;
    private UUID paymentId;
    private PaymentStatus paymentStatus;
    private LocalDate pickupDate;
    private LocalTime pickupStartTime;
    private LocalTime pickupEndTime;
    private Instant paidAt;
    private Instant pickedUpAt;
    private Instant cancelledAt;
    private Instant expiredAt;
    private Instant createdAt;
    private Instant updatedAt;
    private ReviewResponse review;
}
