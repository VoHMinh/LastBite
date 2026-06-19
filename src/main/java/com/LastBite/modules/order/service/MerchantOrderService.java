package com.LastBite.modules.order.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.audit.dto.response.OrderStatusHistoryResponse;
import com.LastBite.modules.audit.enums.AuditActorType;
import com.LastBite.modules.audit.service.AdminAuditLogService;
import com.LastBite.modules.audit.service.OrderStatusHistoryService;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.bag.entity.BagDailyStock;
import com.LastBite.modules.bag.entity.StockAuditLog;
import com.LastBite.modules.bag.enums.DailyStockStatus;
import com.LastBite.modules.bag.enums.StockAuditAction;
import com.LastBite.modules.bag.enums.StockAuditActorType;
import com.LastBite.modules.bag.repository.BagDailyStockRepository;
import com.LastBite.modules.bag.repository.StockAuditLogRepository;
import com.LastBite.modules.merchant.service.StoreAccessService;
import com.LastBite.modules.notification.service.NotificationServicePort;
import com.LastBite.modules.order.dto.request.MerchantCancelOrderRequest;
import com.LastBite.modules.order.dto.response.MerchantOrderResponse;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.payment.entity.Payment;
import com.LastBite.modules.payment.service.PaymentService;
import com.LastBite.modules.promotion.service.VoucherApplicationService;
import com.LastBite.modules.pickup.entity.PickupEvent;
import com.LastBite.modules.pickup.enums.PickupChannel;
import com.LastBite.modules.pickup.enums.PickupEventType;
import com.LastBite.modules.pickup.repository.PickupEventRepository;
import com.LastBite.modules.refund.enums.RefundReason;
import com.LastBite.modules.refund.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantOrderService {

    private static final Set<UserRole> ORDER_ROLES = Set.of(UserRole.MANAGER, UserRole.STAFF);

    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final VoucherApplicationService voucherApplicationService;
    private final StoreAccessService storeAccessService;
    private final UserRepository userRepository;
    private final BagDailyStockRepository stockRepository;
    private final StockAuditLogRepository stockAuditLogRepository;
    private final PickupEventRepository pickupEventRepository;
    private final OrderStatusHistoryService statusHistoryService;
    private final NotificationServicePort notificationService;
    private final AdminAuditLogService auditLogService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<MerchantOrderResponse> list(UUID actorId, UUID storeId, LocalDate date,
                                                    OrderStatus status, Pageable pageable) {
        storeAccessService.require(actorId, storeId, ORDER_ROLES);
        var page = orderRepository.searchStoreOrders(storeId, date, status, pageable)
                .map(this::toResponse);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public MerchantOrderResponse get(UUID actorId, UUID storeId, UUID orderId) {
        storeAccessService.require(actorId, storeId, ORDER_ROLES);
        Order order = orderRepository.findByIdAndStoreId(orderId, storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        return toResponse(order);
    }

    @Transactional
    public MerchantOrderResponse markReady(UUID actorId, UUID storeId, UUID orderId) {
        storeAccessService.require(actorId, storeId, ORDER_ROLES);
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        Order order = loadLockedStoreOrder(orderId, storeId);
        if (order.getStatus() == OrderStatus.READY_FOR_PICKUP) {
            return toResponse(order);
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Chi don da thanh toan moi co the chuyen sang ready");
        }
        OrderStatus previous = order.getStatus();
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        statusHistoryService.record(order, previous, OrderStatus.READY_FOR_PICKUP, actor, AuditActorType.MERCHANT,
                "Merchant marked order ready for pickup", null);
        auditLogService.record(actor, "MERCHANT_ORDER_READY", "ORDER", order.getId(),
                "Merchant marked ready", "storeId=" + storeId);
        notificationService.notifyOrderReadyForPickup(order);
        return toResponse(order);
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public MerchantOrderResponse cancel(UUID actorId, UUID storeId, UUID orderId, MerchantCancelOrderRequest request) {
        storeAccessService.require(actorId, storeId, ORDER_ROLES);
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        Order order = loadLockedStoreOrder(orderId, storeId);
        RefundReason reason = request.getReason();
        if (reason != RefundReason.STORE_CANCELLED && reason != RefundReason.STORE_NO_STOCK) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Merchant chi duoc huy voi STORE_CANCELLED hoac STORE_NO_STOCK");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return toResponse(order);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                && order.getStatus() != OrderStatus.PAID
                && order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Don hang khong the huy o trang thai hien tai");
        }

        OrderStatus previous = order.getStatus();
        Payment payment = paymentService.findByOrderId(order.getId()).orElse(null);
        if (previous == OrderStatus.PENDING_PAYMENT) {
            releaseReservedStock(order, actor, noteOrDefault(request.getNote(), "Merchant cancelled pending order"));
            voucherApplicationService.releaseForOrder(order, "Merchant cancelled pending order");
        } else {
            refundService.createAutoRefund(order, payment, reason, noteOrDefault(request.getNote(),
                    "Merchant cancelled paid order"));
        }

        Instant now = Instant.now(clock);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(now);
        pickupEventRepository.save(PickupEvent.builder()
                .order(order)
                .store(order.getStore())
                .actor(actor)
                .eventType(PickupEventType.REJECTED)
                .channel(PickupChannel.SYSTEM)
                .notes(noteOrDefault(request.getNote(), reason.name()))
                .build());
        statusHistoryService.record(order, previous, OrderStatus.CANCELLED, actor, AuditActorType.MERCHANT,
                "Merchant cancelled order", "reason=" + reason);
        auditLogService.record(actor, "MERCHANT_ORDER_CANCEL", "ORDER", order.getId(),
                trimToNull(request.getNote()), "storeId=" + storeId + ";reason=" + reason);
        notificationService.notifyOrderCancelled(order);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> timeline(UUID actorId, UUID storeId, UUID orderId) {
        storeAccessService.require(actorId, storeId, ORDER_ROLES);
        orderRepository.findByIdAndStoreId(orderId, storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        return statusHistoryService.timeline(orderId);
    }

    private Order loadLockedStoreOrder(UUID orderId, UUID storeId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getStore().getId().equals(storeId)) {
            throw new ApiException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private void releaseReservedStock(Order order, User actor, String reason) {
        BagDailyStock stock = stockRepository.findByBagIdAndDateForUpdate(order.getBag().getId(), order.getPickupDate())
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
        int availableBefore = stock.available();
        stock.setReserved(Math.max(0, stock.getReserved() - order.getQuantity()));
        if (stock.getStatus() == DailyStockStatus.SOLD_OUT && stock.available() > 0) {
            stock.setStatus(DailyStockStatus.ACTIVE);
        }
        stockRepository.save(stock);
        stockAuditLogRepository.save(StockAuditLog.builder()
                .bag(order.getBag())
                .dailyStock(stock)
                .actor(actor)
                .actorType(StockAuditActorType.MERCHANT)
                .action(StockAuditAction.RESERVE_CANCEL)
                .delta(order.getQuantity())
                .quantityBefore(availableBefore)
                .quantityAfter(stock.available())
                .reason(reason)
                .orderId(order.getId())
                .build());
    }

    private MerchantOrderResponse toResponse(Order order) {
        Payment payment = paymentService.findByOrderId(order.getId()).orElse(null);
        return MerchantOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerId(order.getUser().getId())
                .customerName(order.getUser().getFullName())
                .customerPhone(order.getUser().getPhone())
                .storeId(order.getStore().getId())
                .storeName(order.getStore().getName())
                .bagId(order.getBag().getId())
                .bagName(order.getBag().getName())
                .quantity(order.getQuantity())
                .unitPrice(order.getUnitPrice())
                .finalAmount(order.getFinalAmount())
                .status(order.getStatus())
                .refundStatus(order.getRefundStatus())
                .paymentId(payment == null ? null : payment.getId())
                .paymentStatus(payment == null ? null : payment.getStatus())
                .pickupDate(order.getPickupDate())
                .pickupStartTime(order.getPickupStartTime())
                .pickupEndTime(order.getPickupEndTime())
                .paidAt(order.getPaidAt())
                .pickedUpAt(order.getPickedUpAt())
                .cancelledAt(order.getCancelledAt())
                .expiredAt(order.getExpiredAt())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private String noteOrDefault(String note, String fallback) {
        String trimmed = trimToNull(note);
        return trimmed == null ? fallback : trimmed;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
