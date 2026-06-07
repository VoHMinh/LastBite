package com.LastBite.modules.order.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.bag.entity.BagDailyStock;
import com.LastBite.modules.bag.entity.StockAuditLog;
import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.bag.enums.BagStatus;
import com.LastBite.modules.bag.enums.DailyStockStatus;
import com.LastBite.modules.bag.enums.StockAuditAction;
import com.LastBite.modules.bag.repository.BagDailyStockRepository;
import com.LastBite.modules.bag.repository.StockAuditLogRepository;
import com.LastBite.modules.bag.service.impl.BagPricingService;
import com.LastBite.modules.notification.service.NotificationServicePort;
import com.LastBite.modules.order.dto.request.CreateOrderRequest;
import com.LastBite.modules.order.dto.response.OrderResponse;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.order.service.OrderServicePort;
import com.LastBite.modules.store.enums.StoreStatus;
import com.LastBite.modules.store.enums.VerificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
    private final Clock clock;

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public OrderResponse create(UUID userId, CreateOrderRequest request) {
        String idempotencyKey = request.getIdempotencyKey().trim();
        var existingOrder = orderRepository.findByUser_IdAndIdempotencyKey(userId, idempotencyKey);
        if (existingOrder.isPresent()) {
            return toResponse(existingOrder.get());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        BagDailyStock stock = stockRepository.findByBagIdAndDateForUpdate(request.getBagId(), today)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND, "Túi chưa mở bán hôm nay"));
        SurpriseBag bag = stock.getBag();

        validateOrderable(stock, bag, request.getQuantity(), now);

        var price = pricingService.currentPrice(bag, today);
        BigDecimal unitPrice = price.currentSalePrice();
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));
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
                .pickupCode(generatePickupCode())
                .pickupDate(today)
                .pickupStartTime(bag.getPickupStartTime())
                .pickupEndTime(bag.getPickupEndTime())
                .reservedUntil(Instant.now(clock).plus(RESERVATION_TTL))
                .idempotencyKey(idempotencyKey)
                .build();

        int availableBefore = stock.available();
        stock.setReserved(stock.getReserved() + request.getQuantity());
        if (stock.available() <= 0) {
            stock.setStatus(DailyStockStatus.SOLD_OUT);
        }

        order = orderRepository.save(order);
        stockRepository.save(stock);
        writeReserveAudit(bag, stock, user, order.getId(), request.getQuantity(), availableBefore, stock.available());
        notificationService.notifyOrderReserved(order);

        return toResponse(order);
    }

    private void validateOrderable(BagDailyStock stock, SurpriseBag bag, int quantity, LocalTime now) {
        if (quantity > bag.getMaxPerOrder()) {
            throw new ApiException(ErrorCode.INVALID_INPUT,
                    "Số lượng đặt vượt quá giới hạn tối đa của túi này");
        }
        if (bag.getStatus() != BagStatus.ACTIVE) {
            throw new ApiException(ErrorCode.BAG_NOT_FOUND, "Túi hiện không mở bán");
        }
        if (stock.getStore().getStatus() != StoreStatus.ACTIVE
                || stock.getStore().getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Cửa hàng hiện chưa sẵn sàng nhận đơn");
        }
        if (stock.getStatus() != DailyStockStatus.ACTIVE) {
            throw new ApiException(ErrorCode.STOCK_CONFLICT, "Túi hôm nay đã hết hoặc không còn mở bán");
        }
        if (!now.isBefore(bag.getPickupEndTime())) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Đã quá giờ pickup của túi hôm nay");
        }
        if (stock.available() < quantity) {
            throw new ApiException(ErrorCode.STOCK_CONFLICT, "Số lượng túi còn lại không đủ");
        }
    }

    private void writeReserveAudit(SurpriseBag bag, BagDailyStock stock, User actor, UUID orderId,
                                   int quantity, int availableBefore, int availableAfter) {
        auditLogRepository.save(StockAuditLog.builder()
                .bag(bag)
                .dailyStock(stock)
                .actor(actor)
                .action(StockAuditAction.RESERVE)
                .delta(-quantity)
                .quantityBefore(availableBefore)
                .quantityAfter(availableAfter)
                .reason("Khách đặt giữ túi")
                .orderId(orderId)
                .build());
    }

    private String generateOrderNumber() {
        return "LB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String generatePickupCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private OrderResponse toResponse(Order order) {
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
                .status(order.getStatus())
                .pickupCode(order.getPickupCode())
                .pickupDate(order.getPickupDate())
                .pickupStartTime(order.getPickupStartTime())
                .pickupEndTime(order.getPickupEndTime())
                .reservedUntil(order.getReservedUntil())
                .idempotencyKey(order.getIdempotencyKey())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
