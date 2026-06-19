package com.LastBite.modules.pickup.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.util.HashUtil;
import com.LastBite.modules.audit.enums.AuditActorType;
import com.LastBite.modules.audit.service.OrderStatusHistoryService;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.ledger.service.LedgerService;
import com.LastBite.modules.merchant.service.StoreAccessService;
import com.LastBite.modules.notification.service.NotificationServicePort;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.payment.entity.Payment;
import com.LastBite.modules.payment.service.PaymentService;
import com.LastBite.modules.pickup.dto.request.ConfirmPickupRequest;
import com.LastBite.modules.pickup.dto.response.PickupResponse;
import com.LastBite.modules.pickup.entity.PickupEvent;
import com.LastBite.modules.pickup.enums.PickupChannel;
import com.LastBite.modules.pickup.enums.PickupEventType;
import com.LastBite.modules.pickup.repository.PickupEventRepository;
import com.LastBite.modules.store.service.StoreReliabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PickupService {

    private static final Duration PICKUP_GRACE = Duration.ofMinutes(15);
    private static final Duration DISPUTE_WINDOW = Duration.ofDays(30);
    private static final List<OrderStatus> NO_SHOW_CANDIDATES = List.of(OrderStatus.PAID, OrderStatus.READY_FOR_PICKUP);

    private final OrderRepository orderRepository;
    private final PickupEventRepository pickupEventRepository;
    private final UserRepository userRepository;
    private final StoreAccessService storeAccessService;
    private final PaymentService paymentService;
    private final LedgerService ledgerService;
    private final OrderStatusHistoryService statusHistoryService;
    private final NotificationServicePort notificationService;
    private final StoreReliabilityService reliabilityService;
    private final Clock clock;

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public PickupResponse confirm(UUID actorId, ConfirmPickupRequest request) {
        Order order = orderRepository.findByIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        storeAccessService.require(actorId, order.getStore().getId(), Set.of(UserRole.MANAGER, UserRole.STAFF));
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (order.getStatus() == OrderStatus.PICKED_UP) {
            return toResponse(order);
        }
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Don hang chua san sang pickup");
        }
        if (!isWithinPickupWindow(order)) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Khong nam trong khung gio pickup hop le");
        }
        PickupChannel channel = validateCredential(order, request);
        OrderStatus previous = order.getStatus();
        Instant now = Instant.now(clock);
        order.setStatus(OrderStatus.PICKED_UP);
        order.setPickedUpAt(now);
        pickupEventRepository.save(PickupEvent.builder()
                .order(order)
                .store(order.getStore())
                .actor(actor)
                .eventType(PickupEventType.CONFIRMED)
                .channel(channel)
                .notes(trimToNull(request.getNotes()))
                .build());
        Payment payment = paymentService.findByOrderId(order.getId()).orElse(null);
        if (payment != null) {
            ledgerService.recordOrderCompleted(order, payment, now);
        }
        reliabilityService.recordOrderFulfilled(order);
        statusHistoryService.record(order, previous, OrderStatus.PICKED_UP, actor, AuditActorType.MERCHANT,
                "Merchant confirmed pickup", "channel=" + channel);
        return toResponse(order);
    }

    @Transactional
    public int expireNoShows() {
        LocalDate today = LocalDate.now(clock);
        LocalTime cutoff = LocalTime.now(clock).minusMinutes(PICKUP_GRACE.toMinutes());
        int count = 0;
        for (Order order : orderRepository.findOrdersPastPickupWindow(NO_SHOW_CANDIDATES, today, cutoff)) {
            Order locked = orderRepository.findByIdForUpdate(order.getId()).orElse(null);
            if (locked == null || (locked.getStatus() != OrderStatus.PAID && locked.getStatus() != OrderStatus.READY_FOR_PICKUP)) {
                continue;
            }
            OrderStatus previous = locked.getStatus();
            Instant now = Instant.now(clock);
            locked.setStatus(OrderStatus.EXPIRED);
            locked.setExpiredAt(now);
            pickupEventRepository.save(PickupEvent.builder()
                    .order(locked)
                    .store(locked.getStore())
                    .eventType(PickupEventType.NO_SHOW)
                    .channel(PickupChannel.SYSTEM)
                    .notes("Customer missed pickup window")
                    .build());
            paymentService.findByOrderId(locked.getId())
                    .ifPresent(payment -> ledgerService.recordOrderCompleted(locked, payment, now));
            reliabilityService.recordCustomerNoShow(locked);
            Instant disputeWindowUntil = now.plus(DISPUTE_WINDOW);
            notificationService.notifyOrderMissedPickup(locked, disputeWindowUntil);
            statusHistoryService.record(locked, previous, OrderStatus.EXPIRED, null, AuditActorType.SYSTEM,
                    "CUSTOMER_NO_SHOW", "disputeWindowUntil=" + disputeWindowUntil);
            count++;
        }
        return count;
    }

    private PickupChannel validateCredential(Order order, ConfirmPickupRequest request) {
        if (request.getQrToken() != null && !request.getQrToken().isBlank()) {
            if (!HashUtil.matchesSha256(request.getQrToken().trim(), order.getPickupQrTokenHash())) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "QR pickup khong hop le");
            }
            return PickupChannel.QR_SCAN;
        }
        if (request.getPickupCode() != null && !request.getPickupCode().isBlank()) {
            String code = request.getPickupCode().trim().toUpperCase();
            boolean matchesHash = HashUtil.matchesSha256(code, order.getPickupCodeHash());
            boolean matchesLegacy = order.getPickupCode() != null && order.getPickupCode().equalsIgnoreCase(code);
            if (!matchesHash && !matchesLegacy) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "Ma pickup khong hop le");
            }
            return PickupChannel.MANUAL_CODE;
        }
        throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD, "Can truyen pickupCode hoac qrToken");
    }

    private boolean isWithinPickupWindow(Order order) {
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        if (!today.equals(order.getPickupDate())) {
            return false;
        }
        LocalTime graceEnd = order.getPickupEndTime().plusMinutes(PICKUP_GRACE.toMinutes());
        return !now.isBefore(order.getPickupStartTime()) && !now.isAfter(graceEnd);
    }

    private PickupResponse toResponse(Order order) {
        return PickupResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .pickedUpAt(order.getPickedUpAt())
                .build();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
