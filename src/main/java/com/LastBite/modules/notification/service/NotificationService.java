package com.LastBite.modules.notification.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.enums.UserStatus;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.bag.entity.BagDailyStock;
import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.merchant.enums.StoreMemberStatus;
import com.LastBite.modules.merchant.repository.MerchantStoreMemberRepository;
import com.LastBite.modules.notification.dto.request.BroadcastNotificationRequest;
import com.LastBite.modules.notification.dto.response.BroadcastNotificationResponse;
import com.LastBite.modules.notification.entity.AppNotification;
import com.LastBite.modules.notification.enums.NotificationCategory;
import com.LastBite.modules.notification.enums.NotificationReferenceType;
import com.LastBite.modules.notification.enums.NotificationType;
import com.LastBite.modules.notification.repository.AppNotificationRepository;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.promotion.entity.UserVoucher;
import com.LastBite.modules.promotion.entity.VoucherCampaign;
import com.LastBite.modules.store.entity.Store;
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
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService implements NotificationServicePort {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<UserRole> MERCHANT_PUSH_ROLES = Set.of(UserRole.MANAGER, UserRole.STAFF);

    private final AppNotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FavoriteStoreRepository favoriteStoreRepository;
    private final MerchantStoreMemberRepository merchantStoreMemberRepository;
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
    public void notifyPaymentSuccess(Order order) {
        createForUser(order.getUser().getId(), NotificationType.PAYMENT_SUCCESS, NotificationCategory.ORDER,
                "Thanh toan thanh cong",
                "Don " + order.getOrderNumber() + " da duoc thanh toan. Hay den "
                        + order.getStore().getName() + " dung khung gio pickup.",
                null, "/orders/" + order.getId(), NotificationReferenceType.ORDER, order.getId(),
                orderPayload(order), "PAYMENT_SUCCESS:" + order.getUser().getId() + ":" + order.getId());
    }

    @Override
    @Transactional
    public void notifyPaymentFailed(Order order) {
        createForUser(order.getUser().getId(), NotificationType.PAYMENT_FAILED, NotificationCategory.ORDER,
                "Thanh toan that bai",
                "Thanh toan cho don " + order.getOrderNumber() + " chua thanh cong. Ban co the thu lai truoc khi het han giu tui.",
                null, "/orders/" + order.getId(), NotificationReferenceType.ORDER, order.getId(),
                orderPayload(order), "PAYMENT_FAILED:" + order.getUser().getId() + ":" + order.getId() + ":" + order.getUpdatedAt());
    }

    @Override
    @Transactional
    public void notifyOrderExpired(Order order) {
        createForUser(order.getUser().getId(), NotificationType.ORDER_EXPIRED, NotificationCategory.ORDER,
                "Don da het han",
                "Don " + order.getOrderNumber() + " da het han va tui da duoc mo lai cho nguoi khac dat.",
                null, "/orders/" + order.getId(), NotificationReferenceType.ORDER, order.getId(),
                orderPayload(order), "ORDER_EXPIRED:" + order.getUser().getId() + ":" + order.getId());
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
    public void notifyOrderPickedUp(Order order) {
        createForUser(order.getUser().getId(), NotificationType.ORDER_PICKED_UP, NotificationCategory.PICKUP,
                "Cam on ban da cuu thuc an",
                "Don " + order.getOrderNumber() + " da hoan tat. Hen gap lai ban trong lan cuu bua tiep theo!",
                null, "/orders/" + order.getId(), NotificationReferenceType.ORDER, order.getId(),
                orderPayload(order), "ORDER_PICKED_UP:" + order.getUser().getId() + ":" + order.getId());
    }

    @Override
    @Transactional
    public void notifyOrderDelegatePickup(Order order, User delegate) {
        Map<String, String> payload = orderPayload(order);
        payload.put("delegateUserId", delegate.getId().toString());
        payload.put("pickupCode", order.getPickupCode());
        createForUser(delegate.getId(), NotificationType.ORDER_DELEGATE_PICKUP, NotificationCategory.PICKUP,
                "Ban duoc uy quyen nhan don",
                "Dung ma " + order.getPickupCode() + " de nhan don " + order.getOrderNumber()
                        + " tai " + order.getStore().getName() + ".",
                null, "/orders/" + order.getId(), NotificationReferenceType.ORDER, order.getId(),
                payload, "ORDER_DELEGATE_PICKUP:" + delegate.getId() + ":" + order.getId());
    }

    @Override
    @Transactional
    public void notifyImpactMilestone(User user, int savedMeals) {
        Map<String, String> payload = new HashMap<>();
        payload.put("savedMeals", String.valueOf(savedMeals));
        createForUser(user.getId(), NotificationType.IMPACT_MILESTONE, NotificationCategory.SYSTEM,
                "Cot moc moi cua ban",
                "Ban da cuu " + savedMeals + " bua an cung LastBite. Qua dang tu hao!",
                null, "/profile/impact", NotificationReferenceType.USER, user.getId(),
                payload, "IMPACT_MILESTONE:" + user.getId() + ":" + savedMeals);
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
    public void notifyVoucherAvailable(UserVoucher userVoucher) {
        Map<String, String> payload = new HashMap<>();
        payload.put("userVoucherId", userVoucher.getId().toString());
        payload.put("campaignId", userVoucher.getCampaign().getId().toString());
        payload.put("campaignName", userVoucher.getCampaign().getName());
        if (userVoucher.getCode() != null) payload.put("voucherCode", userVoucher.getCode().getCode());
        if (userVoucher.getExpiresAt() != null) payload.put("expiresAt", userVoucher.getExpiresAt().toString());
        createForUser(userVoucher.getUser().getId(), NotificationType.VOUCHER_AVAILABLE, NotificationCategory.PROMOTION,
                "Ban co voucher moi", "Voucher " + userVoucher.getCampaign().getName() + " da nam trong vi cua ban.",
                null, "/vouchers", NotificationReferenceType.CAMPAIGN, userVoucher.getCampaign().getId(),
                payload, "VOUCHER_AVAILABLE:" + userVoucher.getUser().getId() + ":" + userVoucher.getId());
    }

    @Override
    @Transactional
    public void notifyMerchantNewPaidOrder(Order order) {
        createForMerchantStore(order.getStore(), NotificationType.MERCHANT_NEW_PAID_ORDER,
                "Co don da thanh toan moi", "Don " + order.getOrderNumber() + " da duoc thanh toan. Hay chuan bi "
                        + order.getQuantity() + " phan " + order.getBag().getName() + ".",
                "/merchant/orders/" + order.getId(), NotificationReferenceType.ORDER, order.getId(),
                orderPayload(order), "MERCHANT_NEW_PAID_ORDER:" + order.getId());
    }

    @Override
    @Transactional
    public void notifyMerchantOrderPickedUp(Order order) {
        createForMerchantStore(order.getStore(), NotificationType.MERCHANT_ORDER_PICKED_UP,
                "Don da hoan tat", "Don " + order.getOrderNumber() + " da duoc xac nhan pickup.",
                "/merchant/orders/" + order.getId(), NotificationReferenceType.ORDER, order.getId(),
                orderPayload(order), "MERCHANT_ORDER_PICKED_UP:" + order.getId());
    }

    @Override
    @Transactional
    public void notifyMerchantOrderExpired(Order order) {
        createForMerchantStore(order.getStore(), NotificationType.MERCHANT_ORDER_EXPIRED,
                "Don da het han pickup", "Don " + order.getOrderNumber() + " da qua gio pickup. Ban khong can giu phan nay nua.",
                "/merchant/orders/" + order.getId(), NotificationReferenceType.ORDER, order.getId(),
                orderPayload(order), "MERCHANT_ORDER_EXPIRED:" + order.getId());
    }

    @Override
    @Transactional
    public void notifyMerchantPickupSoon(Order order) {
        createForMerchantStore(order.getStore(), NotificationType.MERCHANT_PICKUP_SOON,
                "Sap co khach den nhan", "Don " + order.getOrderNumber() + " bat dau pickup luc "
                        + order.getPickupStartTime().format(TIME_FORMATTER) + ".",
                "/merchant/orders/" + order.getId(), NotificationReferenceType.ORDER, order.getId(),
                orderPayload(order), "MERCHANT_PICKUP_SOON:" + order.getId());
    }

    @Override
    @Transactional
    public void notifyMerchantStockLow(BagDailyStock stock) {
        if (stock.available() <= 0 || stock.available() > 2) return;
        createForMerchantStore(stock.getStore(), NotificationType.MERCHANT_STOCK_LOW,
                "Sap het tui hom nay", stock.getBag().getName() + " chi con " + stock.available() + " phan trong hom nay.",
                "/merchant/bags/" + stock.getBag().getId(), NotificationReferenceType.BAG, stock.getBag().getId(),
                stockPayload(stock), "MERCHANT_STOCK_LOW:" + stock.getId() + ":" + stock.available());
    }
    @Override
    @Transactional
    public void notifyMerchantSetStockReminder(Store store) {
        createForMerchantStore(store, NotificationType.MERCHANT_SET_STOCK_REMINDER,
                "Mo tui cho ngay mai?", "Hay cap nhat so luong tui ngay mai cho " + store.getName() + " de khach co the dat som.",
                "/merchant/stores/" + store.getId() + "/bags", NotificationReferenceType.STORE, store.getId(),
                storePayload(store), "MERCHANT_SET_STOCK_REMINDER:" + store.getId() + ":" + Instant.now().toString().substring(0, 10));
    }

    @Override
    @Transactional
    public void notifyMerchantStoreApproved(Store store) {
        createForMerchantStore(store, NotificationType.MERCHANT_STORE_APPROVED,
                "Cua hang da duoc duyet", store.getName() + " da duoc duyet va co the bat dau ban tui tren LastBite.",
                "/merchant/stores/" + store.getId(), NotificationReferenceType.STORE, store.getId(),
                storePayload(store), "MERCHANT_STORE_APPROVED:" + store.getId());
    }

    @Override
    @Transactional
    public void notifyMerchantStoreRejected(Store store, String reason) {
        Map<String, String> payload = storePayload(store);
        String cleanReason = trimToNull(reason) == null ? "can bo sung thong tin" : reason.trim();
        payload.put("reason", cleanReason);
        createForMerchantStore(store, NotificationType.MERCHANT_STORE_REJECTED,
                "Ho so cua hang can xem lai", store.getName() + " chua duoc duyet. Ly do: " + cleanReason,
                "/merchant/stores/" + store.getId(), NotificationReferenceType.STORE, store.getId(),
                payload, "MERCHANT_STORE_REJECTED:" + store.getId() + ":" + cleanReason);
    }

    @Override
    @Transactional
    public void notifyMerchantBagPaused(SurpriseBag bag) {
        createForMerchantStore(bag.getStore(), NotificationType.MERCHANT_BAG_PAUSED,
                "Tui da tam dung", bag.getName() + " tai " + bag.getStore().getName() + " dang bi tam dung.",
                "/merchant/bags/" + bag.getId(), NotificationReferenceType.BAG, bag.getId(),
                bagPayload(bag), "MERCHANT_BAG_PAUSED:" + bag.getId() + ":" + bag.getUpdatedAt());
    }

    @Override
    @Transactional
    public void notifyMerchantBagResumed(SurpriseBag bag) {
        createForMerchantStore(bag.getStore(), NotificationType.MERCHANT_BAG_RESUMED,
                "Tui da mo lai", bag.getName() + " tai " + bag.getStore().getName() + " da duoc mo ban lai.",
                "/merchant/bags/" + bag.getId(), NotificationReferenceType.BAG, bag.getId(),
                bagPayload(bag), "MERCHANT_BAG_RESUMED:" + bag.getId() + ":" + bag.getUpdatedAt());
    }

    @Override
    @Transactional
    public void notifyMerchantCampaignStarted(VoucherCampaign campaign, Store store) {
        Map<String, String> payload = storePayload(store);
        payload.put("campaignId", campaign.getId().toString());
        payload.put("campaignName", campaign.getName());
        payload.put("startsAt", campaign.getStartsAt().toString());
        payload.put("endsAt", campaign.getEndsAt().toString());
        createForMerchantStore(store, NotificationType.MERCHANT_CAMPAIGN_STARTED,
                "Campaign da duoc duyet", "Campaign " + campaign.getName() + " cua " + store.getName() + " da duoc duyet. Hay chuan bi van hanh.",
                "/merchant/campaigns/" + campaign.getId(), NotificationReferenceType.CAMPAIGN, campaign.getId(),
                payload, "MERCHANT_CAMPAIGN_STARTED:" + store.getId() + ":" + campaign.getId());
    }

    @Override
    @Transactional
    public void notifyAdminStorePendingReview(Store store) {
        createForAdmins(NotificationType.ADMIN_STORE_PENDING_REVIEW,
                "Co cua hang can duyet", store.getName() + " vua gui ho so can admin review.",
                "/admin/stores/" + store.getId() + "/review", NotificationReferenceType.STORE, store.getId(),
                storePayload(store), "ADMIN_STORE_PENDING_REVIEW:" + store.getId() + ":" + store.getUpdatedAt());
    }

    @Override
    @Transactional
    public void notifyAdminRefundRequired(Order order, String failureReason) {
        Map<String, String> payload = orderPayload(order);
        String cleanReason = trimToNull(failureReason) == null ? "" : failureReason.trim();
        payload.put("failureReason", cleanReason);
        createForAdmins(NotificationType.ADMIN_REFUND_REQUIRED,
                "Can xu ly refund thu cong", "Refund cho don " + order.getOrderNumber() + " that bai. Can kiem tra va xu ly ngay.",
                "/admin/refunds", NotificationReferenceType.ORDER, order.getId(),
                payload, "ADMIN_REFUND_REQUIRED:" + order.getId() + ":" + cleanReason);
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

    private void createForMerchantStore(Store store, NotificationType type, String title, String body,
                                        String deepLink, NotificationReferenceType referenceType, UUID referenceId,
                                        Map<String, String> payload, String dedupePrefix) {
        merchantRecipients(store).forEach(user -> createForUser(
                user.getId(),
                type,
                NotificationCategory.MERCHANT,
                title,
                body,
                null,
                deepLink,
                referenceType,
                referenceId,
                payload,
                dedupePrefix + ":" + user.getId()));
    }

    private void createForAdmins(NotificationType type, String title, String body, String deepLink,
                                 NotificationReferenceType referenceType, UUID referenceId,
                                 Map<String, String> payload, String dedupePrefix) {
        userRepository.findAll().stream()
                .filter(user -> user.hasRole(UserRole.ADMIN))
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .forEach(user -> createForUser(
                        user.getId(),
                        type,
                        NotificationCategory.SYSTEM,
                        title,
                        body,
                        null,
                        deepLink,
                        referenceType,
                        referenceId,
                        payload,
                        dedupePrefix + ":" + user.getId()));
    }

    private List<User> merchantRecipients(Store store) {
        return merchantStoreMemberRepository.findAllByStoreIdOrderByCreatedAtAsc(store.getId()).stream()
                .filter(member -> member.getStatus() == StoreMemberStatus.ACTIVE)
                .filter(member -> member.getRole() != null && MERCHANT_PUSH_ROLES.contains(member.getRole().getCode()))
                .map(member -> member.getUser())
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .distinct()
                .toList();
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

    private Map<String, String> stockPayload(BagDailyStock stock) {
        Map<String, String> payload = new HashMap<>();
        payload.put("stockId", stock.getId().toString());
        payload.put("storeId", stock.getStore().getId().toString());
        payload.put("storeName", stock.getStore().getName());
        payload.put("bagId", stock.getBag().getId().toString());
        payload.put("bagName", stock.getBag().getName());
        payload.put("available", String.valueOf(stock.available()));
        payload.put("pickupStartTime", stock.getBag().getPickupStartTime().format(TIME_FORMATTER));
        payload.put("pickupEndTime", stock.getBag().getPickupEndTime().format(TIME_FORMATTER));
        return payload;
    }

    private Map<String, String> storePayload(Store store) {
        Map<String, String> payload = new HashMap<>();
        payload.put("storeId", store.getId().toString());
        payload.put("storeName", store.getName());
        payload.put("storeSlug", store.getSlug());
        return payload;
    }

    private Map<String, String> bagPayload(SurpriseBag bag) {
        Map<String, String> payload = storePayload(bag.getStore());
        payload.put("bagId", bag.getId().toString());
        payload.put("bagName", bag.getName());
        return payload;
    }
    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
