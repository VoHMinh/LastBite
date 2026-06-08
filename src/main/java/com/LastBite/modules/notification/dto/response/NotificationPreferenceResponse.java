package com.LastBite.modules.notification.dto.response;

import com.LastBite.modules.notification.enums.NotificationCategory;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationPreferenceResponse {
    private NotificationCategory category;
    private boolean pushEnabled;
    private boolean emailEnabled;
}
