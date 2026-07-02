package com.LastBite.modules.order.dto.response;

import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.order.enums.OrderRefundStatus;
import com.LastBite.modules.payment.enums.PaymentStatus;
import com.LastBite.modules.promotion.enums.VoucherFundingSource;
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
public class OrderResponse {
    private UUID id;
    private String orderNumber;
    private UUID userId;
    private UUID storeId;
    private String storeName;
    private UUID bagId;
    private String bagName;
    private UUID dailyStockId;
    private String bagImageUrl;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal platformFee;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private UUID voucherCampaignId;
    private UUID voucherCodeId;
    private UUID userVoucherId;
    private String voucherCode;
    private String voucherCampaignName;
    private VoucherFundingSource voucherFundingSource;
    private BigDecimal platformFundedDiscountAmount;
    private BigDecimal merchantFundedDiscountAmount;
    private OrderStatus status;
    private OrderRefundStatus refundStatus;
    private String pickupCode;
    private String pickupQrToken;
    private LocalDate pickupDate;
    private LocalTime pickupStartTime;
    private LocalTime pickupEndTime;
    private Instant reservedUntil;
    private Instant paymentExpiresAt;
    private Instant paidAt;
    private Instant pickedUpAt;
    private Instant cancelledAt;
    private Instant expiredAt;
    private String idempotencyKey;
    private UUID paymentId;
    private PaymentStatus paymentStatus;
    private String paymentProvider;
    private Long paymentOrderCode;
    private String checkoutUrl;
    private String paymentQrCode;
    private Instant createdAt;
    private Instant updatedAt;
    private ReviewResponse review;
    private boolean alreadyLeaveReview;
}
