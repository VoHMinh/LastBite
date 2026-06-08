package com.LastBite.modules.notification.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.notification.dto.response.NotificationResponse;
import com.LastBite.modules.notification.entity.AppNotification;
import com.LastBite.modules.notification.repository.AppNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationInboxService {

    private final AppNotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        var result = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> unread(UUID userId) {
        return notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        AppNotification notification = notificationRepository.findByIdWithRecipient(notificationId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!notification.getRecipient().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId, Instant.now());
    }

    @Transactional
    public void delete(UUID userId, UUID notificationId) {
        AppNotification notification = notificationRepository.findByIdWithRecipient(notificationId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!notification.getRecipient().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        notificationRepository.delete(notification);
    }

    private NotificationResponse toResponse(AppNotification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .category(notification.getCategory())
                .title(notification.getTitle())
                .body(notification.getBody())
                .imageUrl(notification.getImageUrl())
                .deepLink(notification.getDeepLink())
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .payload(notification.getPayload())
                .read(notification.isRead())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
