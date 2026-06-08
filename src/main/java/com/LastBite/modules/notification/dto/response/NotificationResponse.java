package com.LastBite.modules.notification.dto.response;

import com.LastBite.modules.notification.enums.NotificationCategory;
import com.LastBite.modules.notification.enums.NotificationReferenceType;
import com.LastBite.modules.notification.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {
    private UUID id;
    private NotificationType type;
    private NotificationCategory category;
    private String title;
    private String body;
    private String imageUrl;
    private String deepLink;
    private NotificationReferenceType referenceType;
    private UUID referenceId;
    private Map<String, String> payload;
    private boolean read;
    private Instant readAt;
    private Instant createdAt;
}
