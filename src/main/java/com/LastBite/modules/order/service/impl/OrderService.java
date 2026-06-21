package com.LastBite.modules.order.service.impl;

import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.audit.dto.response.OrderStatusHistoryResponse;
import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.security.SensitiveDataCipher;
import com.LastBite.common.util.HashUtil;
import com.LastBite.modules.audit.enums.AuditActorType;
import com.LastBite.modules.audit.service.OrderStatusHistoryService;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.bag.entity.BagDailyStock;
import com.LastBite.modules.bag.entity.StockAuditLog;
import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.bag.enums.BagStatus;
import com.LastBite.modules.bag.enums.DailyStockStatus;
import com.LastBite.modules.bag.enums.StockAuditAction;
import com.LastBite.modules.bag.enums.StockAuditActorType;
import com.LastBite.modules.bag.repository.BagDailyStockRepository;
import com.LastBite.modules.bag.repository.StockAuditLogRepository;
import com.LastBite.modules.bag.service.impl.BagPricingService;
import com.LastBite.modules.notification.service.NotificationServicePort;
import com.LastBite.modules.order.dto.request.CreateOrderRequest;
import com.LastBite.modules.order.dto.response.OrderResponse;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.enums.OrderRefundStatus;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.order.service.OrderServicePort;
import com.LastBite.modules.payment.entity.Payment;
import com.LastBite.modules.payment.service.PaymentService;
import com.LastBite.modules.promotion.dto.response.VoucherValidationResponse;
import com.LastBite.modules.promotion.service.VoucherApplicationResult;
import com.LastBite.modules.promotion.service.VoucherApplicationService;
import com.LastBite.modules.refund.enums.RefundReason;
import com.LastBite.modules.refund.service.RefundService;
import com.LastBite.modules.store.enums.StoreStatus;
import com.LastBite.modules.store.enums.VerificationStatus;
import com.LastBite.modules.store.service.StoreCalendarService;
import com.LastBite.modules.store.service.StoreReliabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService implements OrderServicePort {

    private static final Duration RESERVATION_TTL = Duration.ofMinutes(10);

    private final OrderRepository orderRepository;
    private final BagDailyStockRepository stockRepository;
    private final StockAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final BagPricingService pricingService;
    private final NotificationServicePort notificationService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final VoucherApplicationService voucherApplicationService;
    private final StoreCalendarService storeCalendarService;
    private final StoreReliabilityService reliabilityService;
    private final OrderStatusHistoryService statusHistoryService;
    private final SensitiveDataCipher sensitiveDataCipher;
    private final Clock clock;

    @Override
    @Transactional
    @CacheEvict(value = {"bag-discovery", "home-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public OrderResponse create(UUID userId, CreateOrderRequest request) {
        String idempotencyKey = request.getIdempotencyKey().trim();
        var existingOrder = orderRepository.findByUser_IdAndIdempotencyKey(userId, idempotencyKey);
        if (existingOrder.isPresent()) {
            Order order = existingOrder.get();
            return toResponse(order, paymentService.findByOrderId(order.getId()).orElse(null), null);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        BagDailyStock stock = stockRepository.findByBagIdAndDateForUpdate(request.getBagId(), today)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND, "Tui chua mo ban hom nay"));
        SurpriseBag bag = stock.getBag();

        validateOrderable(stock, bag, request.getQuantity(), now);

        var price = pricingService.currentPrice(bag, today);
        BigDecimal unitPrice = price.currentSalePrice();
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));
        Instant reservedUntil = Instant.now(clock).plus(RESERVATION_TTL);
        String pickupCode = generatePickupCode();
        String pickupQrToken = generatePickupQrToken();

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .user(user)
                .store(stock.getStore())
                .bag(bag)
                .dailyStock(stock)
                .quantity(request.getQuantity())
                .unitPrice(unitPrice)
                .platformFee(bag.getPlatformFee())
                .subtotal(subtotal)
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(subtotal)
                .status(OrderStatus.PENDING_PAYMENT)
                .refundStatus(OrderRefundStatus.NONE)
                .pickupCode(pickupCode)
                .pickupCodeHash(HashUtil.sha256(pickupCode))
                .pickupQrTokenHash(HashUtil.sha256(pickupQrToken))
                .pickupQrTokenEncrypted(sensitiveDataCipher.encrypt(pickupQrToken))
                .pickupDate(today)
                .pickupStartTime(bag.getPickupStartTime())
                .pickupEndTime(bag.getPickupEndTime())
                .reservedUntil(reservedUntil)
                .paymentExpiresAt(reservedUntil)
                .idempotencyKey(idempotencyKey)
                .build();

        order = orderRepository.save(order);
        VoucherApplicationResult voucher = voucherApplicationService.reserveForOrder(user, bag, subtotal,
                order, request.getVoucherCode(), request.getUserVoucherId(), reservedUntil);
        order.setDiscountAmount(voucher.getDiscountAmount());
        order.setFinalAmount(voucher.getFinalAmount());

        int availableBefore = stock.available();
        stock.setReserved(stock.getReserved() + request.getQuantity());
        if (stock.available() <= 0) {
            stock.setStatus(DailyStockStatus.SOLD_OUT);
        }
        stockRepository.save(stock);
        writeReserveAudit(bag, stock, user, order.getId(), request.getQuantity(), availableBefore, stock.available());
        statusHistoryService.record(order, null, OrderStatus.PENDING_PAYMENT, user, AuditActorType.CUSTOMER,
                "Customer reserved surprise bag", null);
        Payment payment = paymentService.createPaymentForOrder(order);
        notificationService.notifyOrderReserved(order);

        return toResponse(order, payment, pickupQrToken);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse get(UUID userId, UUID orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        return toResponse(order, paymentService.findByOrderId(orderId).orElse(null), decryptPickupQrToken(order));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(UUID userId, OrderStatus status, OrderRefundStatus refundStatus,
                                            LocalDate pickupDateFrom, LocalDate pickupDateTo, Pageable pageable) {
        var page = orderRepository.searchCustomerOrders(userId, status, refundStatus,
                pickupDateFrom, pickupDateTo, pageable)
                .map(order -> toResponse(order, paymentService.findByOrderId(order.getId()).orElse(null), null));
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> timeline(UUID userId, UUID orderId) {
        orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        return statusHistoryService.timeline(orderId);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"bag-discovery", "home-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public OrderResponse cancel(UUID userId, UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.EXPIRED) {
            return toResponse(order, paymentService.findByOrderId(orderId).orElse(null), null);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT && order.getStatus() != OrderStatus.PAID) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Don hang khong the huy o trang thai hien tai");
        }
        OrderStatus previous = order.getStatus();
        Payment payment = paymentService.findByOrderId(orderId).orElse(null);
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            ensurePendingPaymentCanBeCancelled(order, payment);
            releaseReservedStock(order, "Khach huy truoc khi thanh toan");
            voucherApplicationService.releaseForOrder(order, "Customer cancelled before payment");
            paymentService.cancelPendingPayment(payment, "Customer cancelled before payment");
        } else {
            ensurePaidOrderCanBeCancelled(order);
            refundService.createAutoRefund(order, payment, RefundReason.CUSTOMER_COMPLAINT,
                    "Customer cancelled before pickup window");
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now(clock));
        statusHistoryService.record(order, previous, OrderStatus.CANCELLED, order.getUser(), AuditActorType.CUSTOMER,
                "Customer cancelled order", null);
        notificationService.notifyOrderCancelled(order);
        return toResponse(order, payment, null);
    }

    private void validateOrderable(BagDailyStock stock, SurpriseBag bag, int quantity, LocalTime now) {
        if (quantity > bag.getMaxPerOrder()) {
            throw new ApiException(ErrorCode.INVALID_INPUT,
                    "So luong dat vuot qua gioi han toi da cua tui nay");
        }
        if (bag.getStatus() != BagStatus.ACTIVE) {
            throw new ApiException(ErrorCode.BAG_NOT_FOUND, "Tui hien khong mo ban");
        }
        if (stock.getStore().getStatus() != StoreStatus.ACTIVE
                || stock.getStore().getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Cua hang hien chua san sang nhan don");
        }
        reliabilityService.ensureStoreCanReceiveOrders(stock.getStore());
        if (stock.getStatus() != DailyStockStatus.ACTIVE) {
            throw new ApiException(ErrorCode.STOCK_CONFLICT, "Tui hom nay da het hoac khong con mo ban");
        }
        if (!storeCalendarService.supportsPickupWindow(stock.getStore(), stock.getDate(),
                bag.getPickupStartTime(), bag.getPickupEndTime())) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Cua hang dong cua hoac co gio dac biet khong phu hop");
        }
        if (!now.isBefore(bag.getPickupEndTime())) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Da qua gio pickup cua tui hom nay");
        }
        if (stock.available() < quantity) {
            throw new ApiException(ErrorCode.STOCK_CONFLICT, "So luong tui con lai khong du");
        }
    }

    private void writeReserveAudit(SurpriseBag bag, BagDailyStock stock, User actor, UUID orderId,
                                   int quantity, int availableBefore, int availableAfter) {
        auditLogRepository.save(StockAuditLog.builder()
                .bag(bag)
                .dailyStock(stock)
                .actor(actor)
                .actorType(StockAuditActorType.CUSTOMER)
                .action(StockAuditAction.RESERVE)
                .delta(-quantity)
                .quantityBefore(availableBefore)
                .quantityAfter(availableAfter)
                .reason("Khach dat giu tui")
                .orderId(orderId)
                .build());
    }

    private void releaseReservedStock(Order order, String reason) {
        BagDailyStock stock = stockRepository.findByBagIdAndDateForUpdate(order.getBag().getId(), order.getPickupDate())
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
        int availableBefore = stock.available();
        stock.setReserved(Math.max(0, stock.getReserved() - order.getQuantity()));
        if (stock.getStatus() == DailyStockStatus.SOLD_OUT && stock.available() > 0) {
            stock.setStatus(DailyStockStatus.ACTIVE);
        }
        stockRepository.save(stock);
        auditLogRepository.save(StockAuditLog.builder()
                .bag(order.getBag())
                .dailyStock(stock)
                .actor(order.getUser())
                .actorType(StockAuditActorType.CUSTOMER)
                .action(StockAuditAction.RESERVE_CANCEL)
                .delta(order.getQuantity())
                .quantityBefore(availableBefore)
                .quantityAfter(stock.available())
                .reason(reason)
                .orderId(order.getId())
                .build());
    }

    private String generateOrderNumber() {
        return "LB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String generatePickupCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private String generatePickupQrToken() {
        return "pk_" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private void ensurePendingPaymentCanBeCancelled(Order order, Payment payment) {
        Instant expiresAt = payment != null && payment.getExpiresAt() != null
                ? payment.getExpiresAt()
                : order.getPaymentExpiresAt() == null ? order.getReservedUntil() : order.getPaymentExpiresAt();
        if (expiresAt != null && Instant.now(clock).isAfter(expiresAt)) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Don hang da het han thanh toan");
        }
    }

    private void ensurePaidOrderCanBeCancelled(Order order) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cancelCutoff = LocalDateTime.of(order.getPickupDate(), order.getPickupStartTime()).minusHours(2);
        if (!now.isBefore(cancelCutoff)) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Chi co the huy truoc gio pickup it nhat 2 tieng");
        }
    }

    private String decryptPickupQrToken(Order order) {
        if (order.getPickupQrTokenEncrypted() == null || order.getPickupQrTokenEncrypted().isBlank()) {
            return null;
        }
        return sensitiveDataCipher.decrypt(order.getPickupQrTokenEncrypted());
    }

    private OrderResponse toResponse(Order order, Payment payment, String pickupQrToken) {
        VoucherValidationResponse voucher = order.getId() == null ? null : voucherApplicationService.snapshotForOrder(order.getId());
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUser().getId())
                .storeId(order.getStore().getId())
                .storeName(order.getStore().getName())
                .bagId(order.getBag().getId())
                .bagName(order.getBag().getName())
                .dailyStockId(order.getDailyStock().getId())
                .quantity(order.getQuantity())
                .unitPrice(order.getUnitPrice())
                .platformFee(order.getPlatformFee())
                .subtotal(order.getSubtotal())
                .discountAmount(order.getDiscountAmount())
                .finalAmount(order.getFinalAmount())
                .voucherCampaignId(voucher == null ? null : voucher.getCampaignId())
                .voucherCodeId(voucher == null ? null : voucher.getVoucherCodeId())
                .userVoucherId(voucher == null ? null : voucher.getUserVoucherId())
                .voucherCode(voucher == null ? null : voucher.getVoucherCode())
                .voucherCampaignName(voucher == null ? null : voucher.getCampaignName())
                .voucherFundingSource(voucher == null ? null : voucher.getFundingSource())
                .platformFundedDiscountAmount(voucher == null ? BigDecimal.ZERO : voucher.getPlatformFundedAmount())
                .merchantFundedDiscountAmount(voucher == null ? BigDecimal.ZERO : voucher.getMerchantFundedAmount())
                .status(order.getStatus())
                .refundStatus(order.getRefundStatus())
                .pickupCode(order.getPickupCode())
                .pickupQrToken(pickupQrToken)
                .pickupDate(order.getPickupDate())
                .pickupStartTime(order.getPickupStartTime())
                .pickupEndTime(order.getPickupEndTime())
                .reservedUntil(order.getReservedUntil())
                .paymentExpiresAt(order.getPaymentExpiresAt())
                .paidAt(order.getPaidAt())
                .pickedUpAt(order.getPickedUpAt())
                .cancelledAt(order.getCancelledAt())
                .expiredAt(order.getExpiredAt())
                .idempotencyKey(order.getIdempotencyKey())
                .paymentId(payment == null ? null : payment.getId())
                .paymentStatus(payment == null ? null : payment.getStatus())
                .paymentProvider(payment == null ? null : payment.getProvider().name())
                .paymentOrderCode(payment == null ? null : payment.getProviderOrderCode())
                .checkoutUrl(payment == null ? null : payment.getCheckoutUrl())
                .paymentQrCode(payment == null ? null : payment.getQrCode())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
