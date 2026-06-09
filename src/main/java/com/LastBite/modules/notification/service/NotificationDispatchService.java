package com.LastBite.modules.notification.service;

import com.LastBite.modules.notification.repository.AppNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private final AppNotificationRepository notificationRepository;
    private final PushNotificationService pushNotificationService;

    @Async
    @Transactional(readOnly = true)
    public void dispatchPush(UUID notificationId) {
        try {
            notificationRepository.findByIdWithRecipient(notificationId)
                    .ifPresent(pushNotificationService::sendNotification);
        } catch (Exception e) {
            log.error("Failed to dispatch push for notification {}: {}", notificationId, e.getMessage(), e);
        }
    }
}
