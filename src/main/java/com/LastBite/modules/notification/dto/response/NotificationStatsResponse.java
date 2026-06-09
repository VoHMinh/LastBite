package com.LastBite.modules.notification.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationStatsResponse {
    private long activeDeviceTokens;
    private boolean fcmAvailable;
}
