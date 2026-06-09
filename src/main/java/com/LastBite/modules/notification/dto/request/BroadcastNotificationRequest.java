package com.LastBite.modules.notification.dto.request;

import com.LastBite.modules.notification.enums.NotificationCategory;
import com.LastBite.modules.notification.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class BroadcastNotificationRequest {

    @NotNull(message = "Notification type is required")
    private NotificationType type;

    @NotNull(message = "Category is required")
    private NotificationCategory category;

    @NotBlank(message = "Title is required")
    @Size(max = 120, message = "Title must not exceed 120 characters")
    private String title;

    @NotBlank(message = "Body is required")
    @Size(max = 500, message = "Body must not exceed 500 characters")
    private String body;

    @Size(max = 500, message = "Image URL must not exceed 500 characters")
    private String imageUrl;

    @Size(max = 500, message = "Deep link must not exceed 500 characters")
    private String deepLink;

    private Map<String, String> payload;
}
