package com.LastBite.modules.notification.service;

import com.LastBite.modules.notification.entity.AppNotification;
import com.LastBite.modules.notification.entity.NotificationDelivery;
import com.LastBite.modules.notification.entity.NotificationDevice;
import com.LastBite.modules.notification.enums.DeliveryChannel;
import com.LastBite.modules.notification.enums.DeliveryStatus;
import com.LastBite.modules.notification.repository.NotificationDeliveryRepository;
import com.LastBite.modules.notification.repository.NotificationDeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class PushNotificationService {

    private static final int FCM_MULTICAST_BATCH_SIZE = 500;

    private final FcmService fcmService;
    private final NotificationDeviceRepository deviceRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationPreferenceService preferenceService;

    @Autowired
    public PushNotificationService(@Autowired(required = false) FcmService fcmService,
                                   NotificationDeviceRepository deviceRepository,
                                   NotificationDeliveryRepository deliveryRepository,
                                   NotificationPreferenceService preferenceService) {
        this.fcmService = fcmService;
        this.deviceRepository = deviceRepository;
        this.deliveryRepository = deliveryRepository;
        this.preferenceService = preferenceService;
    }

    public boolean isFcmAvailable() {
        return fcmService != null && fcmService.isAvailable();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int sendNotification(AppNotification notification) {
        log.debug("[Push] Attempting send. recipientId={}, category={}, title={}, fcmAvailable={}",
                notification.getRecipient().getId(), notification.getCategory(), notification.getTitle(), isFcmAvailable());
        if (!preferenceService.isPushEnabled(notification.getRecipient().getId(), notification.getCategory())) {
            log.debug("[Push] Skipped: push disabled for user={}, category={}", notification.getRecipient().getId(), notification.getCategory());
            saveSkipped(notification, null, "User disabled push for " + notification.getCategory());
            return 0;
        }
        if (!isFcmAvailable()) {
            log.debug("[Push] Skipped: FCM is disabled or unavailable");
            saveSkipped(notification, null, "FCM is disabled or unavailable");
            return 0;
        }

        List<NotificationDevice> devices = deviceRepository.findByUserIdAndActiveTrue(notification.getRecipient().getId());
        log.debug("[Push] Found {} active device(s) for userId={}", devices.size(), notification.getRecipient().getId());
        if (devices.isEmpty()) {
            log.debug("[Push] Skipped: no active devices");
            saveSkipped(notification, null, "No active notification devices");
            return 0;
        }

        int success = 0;
        for (int i = 0; i < devices.size(); i += FCM_MULTICAST_BATCH_SIZE) {
            List<NotificationDevice> batch = devices.subList(i, Math.min(i + FCM_MULTICAST_BATCH_SIZE, devices.size()));
            for (NotificationDevice device : batch) {
                FcmSendResult result = fcmService.sendNotification(
                        device.getDeviceToken(),
                        device.getDeviceType(),
                        notification.getTitle(),
                        notification.getBody(),
                        notification.getImageUrl(),
                        notification.getDeepLink(),
                        notification.getPayload());
                saveDelivery(notification, device, result);
                if (result.messageId() != null) {
                    success++;
                }
                if (result.invalidToken()) {
                    device.setActive(false);
                    deviceRepository.save(device);
                }
            }
        }
        return success;
    }

    public long countActiveTokens() {
        return deviceRepository.countByActiveTrue();
    }

    private void saveSkipped(AppNotification notification, NotificationDevice device, String reason) {
        deliveryRepository.save(NotificationDelivery.builder()
                .notification(notification)
                .device(device)
                .channel(DeliveryChannel.FCM)
                .status(DeliveryStatus.SKIPPED)
                .errorMessage(reason)
                .build());
    }

    private void saveDelivery(AppNotification notification, NotificationDevice device, FcmSendResult result) {
        DeliveryStatus status = result.messageId() == null ? DeliveryStatus.FAILED : DeliveryStatus.SENT;
        deliveryRepository.save(NotificationDelivery.builder()
                .notification(notification)
                .device(device)
                .channel(DeliveryChannel.FCM)
                .status(status)
                .providerMessageId(result.messageId())
                .errorMessage(result.errorMessage())
                .sentAt(status == DeliveryStatus.SENT ? Instant.now() : null)
                .build());
    }
}
