package com.LastBite.modules.notification.dto.response;

import com.LastBite.modules.notification.enums.DeviceType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class NotificationDeviceResponse {
    private UUID id;
    private String deviceToken;
    private DeviceType deviceType;
    private String appVersion;
    private boolean active;
    private Instant lastSeenAt;
    private Instant createdAt;
}
