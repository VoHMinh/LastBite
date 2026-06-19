package com.LastBite.modules.notification.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.enums.UserStatus;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.bag.entity.BagDailyStock;
import com.LastBite.modules.notification.dto.request.BroadcastNotificationRequest;
import com.LastBite.modules.notification.dto.response.BroadcastNotificationResponse;
import com.LastBite.modules.notification.entity.AppNotification;
import com.LastBite.modules.notification.enums.NotificationCategory;
import com.LastBite.modules.notification.enums.NotificationReferenceType;
import com.LastBite.modules.notification.enums.NotificationType;
import com.LastBite.modules.notification.repository.AppNotificationRepository;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.user.repository.FavoriteStoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService implements NotificationServicePort {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AppNotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FavoriteStoreRepository favoriteStoreRepository;
    private final NotificationDispatchService dispatchService;

    @Override
    @Transactional
    public void notifyOrderReserved(Order order) {
        Map<String, String> payload = new HashMap<>();
        payload.put("orderId", order.getId().toString());
        payload.put("orderNumber", order.getOrderNumber());
        payload.put("bagId", order.getBag().getId().toString());
        payload.put("bagName", order.getBag().getName());
        payload.put("storeId", order.getStore().getId().toString());
        payload.put("storeName", order.getStore().getName());
        payload.put("reservedUntil", order.getReservedUntil().toString());

        createForUser(
                order.getUser().getId(),
                NotificationType.ORDER_RESERVED,
                NotificationCategory.ORDER,
                "Da giu tui thanh cong",
                "Vui long thanh toan don " + order.getOrderNumber() + " truoc khi het han giu tui.",
                null,
                "/orders/" + order.getId(),
                NotificationReferenceType.ORDER,
                order.getId(),
                payload,
                "ORDER_RESERVED:" + order.getUser().getId() + ":" + order.getId());
    }

    @Override
    @Transactional
    public void notifyOrderReadyForPickup(Order order) {
        createForUser(
                order.getUser().getId(),
                NotificationType.ORDER_READY_FOR_PICKUP,
                NotificationCategory.PICKUP,
                "Don da san sang",
                "Don " + order.getOrderNumber() + " tai " + order.getStore().getName() + " da san sang de nhan.",
                null,
                "/orders/" + order.getId(),
                NotificationReferenceType.ORDER,
                order.getId(),
                orderPayload(order),
                "ORDER_READY_FOR_PICKUP:" + order.getUser().getId() + ":" + order.getId());
    }

    @Override
    @Transactional
    public void notifyOrderCancelled(Order order) {
        createForUser(
                order.getUser().getId(),
                NotificationType.ORDER_CANCELLED,
                NotificationCategory.ORDER,
                "Don hang da bi huy",
                "Don " + order.getOrderNumber() + " da bi huy. Kiem tra chi tiet don de xem trang thai hoan tien.",
                null,
                "/orders/" + order.getId(),
                NotificationReferenceType.ORDER,
                order.getId(),
                orderPayload(order),
                "ORDER_CANCELLED:" + order.getUser().getId() + ":" + order.getId() + ":" + order.getCancelledAt());
    }

    @Override
    @Transactional
    public void notifyOrderRefunded(Order order) {
        createForUser(
                order.getUser().getId(),
                NotificationType.ORDER_REFUNDED,
                NotificationCategory.ORDER,
                "Hoan tien thanh cong",
                "Don " + order.getOrderNumber() + " da duoc cap nhat trang thai hoan tien.",
                null,
                "/orders/" + order.getId(),
                NotificationReferenceType.ORDER,
                order.getId(),
                orderPayload(order),
                "ORDER_REFUNDED:" + order.getUser().getId() + ":" + order.getId() + ":" + order.getRefundStatus());
    }

    @Override
    @Transactional
    public void notifyOrderMissedPickup(Order order, Instant disputeWindowUntil) {
        Map<String, String> payload = orderPayload(order);
        payload.put("disputeWindowUntil", disputeWindowUntil.toString());
        createForUser(
                order.getUser().getId(),
                NotificationType.ORDER_MISSED_PICKUP,
                NotificationCategory.PICKUP,
                "Da qua gio nhan don",
                "Don " + order.getOrderNumber() + " da qua khung gio pickup. No-show khong duoc auto-hoan tien; neu cua hang co van de, ban co the gui dispute trong 30 ngay.",
                null,
                "/orders/" + order.getId(),
                NotificationReferenceType.ORDER,
                order.getId(),
                payload,
                "ORDER_MISSED_PICKUP:" + order.getUser().getId() + ":" + order.getId());
    }

    @Override
    @Transactional
    public void notifyPaymentExpiring(Order order) {
        createForUser(
                order.getUser().getId(),
                NotificationType.PAYMENT_EXPIRING,
                NotificationCategory.ORDER,
                "Sap het han thanh toan",
                "Don " + order.getOrderNumber() + " sap het han giu tui. Thanh toan ngay de khong mat slot.",
                null,
                "/orders/" + order.getId(),
                NotificationReferenceType.ORDER,
                order.getId(),
                orderPayload(order),
                "PAYMENT_EXPIRING:" + order.getUser().getId() + ":" + order.getId());
    }

    @Override
    @Transactional
    public void notifyPickupReminder(Order order, int minutesBefore) {
        NotificationType type = switch (minutesBefore) {
            case 60 -> NotificationType.PICKUP_REMINDER_60M;
            case 30 -> NotificationType.PICKUP_REMINDER_30M;
            case 10 -> NotificationType.PICKUP_REMINDER_10M;
            default -> NotificationType.PICKUP_REMINDER_30M;
        };
        createForUser(
                order.getUser().getId(),
                type,
                NotificationCategory.PICKUP,
                "Sap toi gio nhan box",
                "Con khoang " + minutesBefore + " phut nua toi gio nhan " + order.getBag().getName()
                        + " tai " + order.getStore().getName() + ".",
                null,
                "/orders/" + order.getId(),
                NotificationReferenceType.ORDER,
                order.getId(),
                orderPayload(order),
                type + ":" + order.getUser().getId() + ":" + order.getId());
    }

    @Override
    @Transactional
    public void notifyPickupWindowOpen(Order order) {
        createForUser(
                order.getUser().getId(),
                NotificationType.PICKUP_WINDOW_OPEN,
                NotificationCategory.PICKUP,
                "Da den gio nhan box",
                "Ban co the den " + order.getStore().getName() + " de nhan " + order.getBag().getName() + ".",
                null,
                "/orders/" + order.getId(),
                NotificationReferenceType.ORDER,
                order.getId(),
                orderPayload(order),
                "PICKUP_WINDOW_OPEN:" + order.getUser().getId() + ":" + order.getId());
    }

    @Override
    @Transactional
    public void notifyPickupEndingSoon(Order order) {
        createForUser(
                order.getUser().getId(),
                NotificationType.PICKUP_ENDING_SOON,
                NotificationCategory.PICKUP,
                "Sap het gio nhan box",
                "Khung gio nhan don " + order.getOrderNumber() + " sap ket thuc luc "
                        + order.getPickupEndTime().format(TIME_FORMATTER) + ".",
                null,
                "/orders/" + order.getId(),
                NotificationReferenceType.ORDER,
                order.getId(),
                orderPayload(order),
                "PICKUP_ENDING_SOON:" + order.getUser().getId() + ":" + order.getId());
    }

    @Override
    @Transactional
    public void notifyFavoriteStoreStockAvailable(BagDailyStock stock) {
        if (stock.available() <= 0) {
            return;
        }

        var favorites = favoriteStoreRepository.findByStoreId(stock.getStore().getId());
        for (var favorite : favorites) {
            User user = favorite.getUser();
            if (user.getStatus() != UserStatus.ACTIVE) {
                continue;
            }

            Map<String, String> payload = new HashMap<>();
            payload.put("storeId", stock.getStore().getId().toString());
            payload.put("storeName", stock.getStore().getName());
            payload.put("bagId", stock.getBag().getId().toString());
            payload.put("bagName", stock.getBag().getName());
            payload.put("available", String.valueOf(stock.available()));
            payload.put("pickupStartTime", stock.getBag().getPickupStartTime().format(TIME_FORMATTER));
            payload.put("pickupEndTime", stock.getBag().getPickupEndTime().format(TIME_FORMATTER));

            createForUser(
                    user.getId(),
                    NotificationType.FAVORITE_STORE_STOCK_AVAILABLE,
                    NotificationCategory.STORE,
                    stock.getStore().getName() + " vua co tui hom nay",
                    stock.getBag().getName() + " dang con " + stock.available() + " phan. Dat som truoc khi het.",
                    firstPhoto(stock),
                    "/stores/" + stock.getStore().getSlug(),
                    NotificationReferenceType.BAG,
                    stock.getBag().getId(),
                    payload,
                    "FAVORITE_STOCK:" + user.getId() + ":" + stock.getId() + ":" + stock.available());
        }
    }

    @Override
    @Transactional
    public BroadcastNotificationResponse broadcastToCustomers(BroadcastNotificationRequest request) {
        List<User> customers = userRepository.findAll().stream()
                .filter(user -> user.hasRole(UserRole.CUSTOMER))
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .toList();

        int created = 0;
        for (User user : customers) {
            AppNotification notification = createForUser(
                    user.getId(),
                    request.getType(),
                    request.getCategory(),
                    request.getTitle(),
                    request.getBody(),
                    request.getImageUrl(),
                    request.getDeepLink(),
                    NotificationReferenceType.CAMPAIGN,
                    null,
                    request.getPayload(),
                    "BROADCAST:" + request.getType() + ":" + user.getId() + ":" + System.nanoTime());
            if (notification != null) {
                created++;
            }
        }
        return BroadcastNotificationResponse.builder()
                .notificationsCreated(created)
                .build();
    }

    public AppNotification createForUser(UUID userId, NotificationType type, NotificationCategory category,
                                         String title, String body, String imageUrl, String deepLink,
                                         NotificationReferenceType referenceType, UUID referenceId,
                                         Map<String, String> payload, String dedupeKey) {
        if (dedupeKey != null && notificationRepository.findByDedupeKey(dedupeKey).isPresent()) {
            return null;
        }

        User recipient = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        AppNotification notification = AppNotification.builder()
                .recipient(recipient)
                .type(type)
                .category(category)
                .title(title.trim())
                .body(body.trim())
                .imageUrl(trimToNull(imageUrl))
                .deepLink(trimToNull(deepLink))
                .referenceType(referenceType)
                .referenceId(referenceId)
                .payload(payload == null ? Map.of() : payload)
                .dedupeKey(dedupeKey)
                .read(false)
                .build();

        AppNotification saved = notificationRepository.save(notification);
        dispatchAfterCommit(saved.getId());
        return saved;
    }

    private void dispatchAfterCommit(UUID notificationId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchService.dispatchPush(notificationId);
                }
            });
        } else {
            dispatchService.dispatchPush(notificationId);
        }
    }

    private String firstPhoto(BagDailyStock stock) {
        String[] photos = stock.getBag().getPhotos();
        return photos == null || photos.length == 0 ? null : photos[0];
    }

    private Map<String, String> orderPayload(Order order) {
        Map<String, String> payload = new HashMap<>();
        payload.put("orderId", order.getId().toString());
        payload.put("orderNumber", order.getOrderNumber());
        payload.put("bagId", order.getBag().getId().toString());
        payload.put("bagName", order.getBag().getName());
        payload.put("storeId", order.getStore().getId().toString());
        payload.put("storeName", order.getStore().getName());
        payload.put("pickupDate", order.getPickupDate().toString());
        payload.put("pickupStartTime", order.getPickupStartTime().format(TIME_FORMATTER));
        payload.put("pickupEndTime", order.getPickupEndTime().format(TIME_FORMATTER));
        return payload;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
