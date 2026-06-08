package com.LastBite.modules.notification.dto.request;

import com.LastBite.modules.notification.enums.NotificationCategory;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateNotificationPreferenceRequest {

    @NotNull(message = "Category is required")
    private NotificationCategory category;

    private Boolean pushEnabled;

    private Boolean emailEnabled;
}
