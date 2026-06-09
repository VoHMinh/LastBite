package com.LastBite.modules.notification.dto.request;

import com.LastBite.modules.notification.enums.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterNotificationDeviceRequest {

    @NotBlank(message = "Device token is required")
    @Size(max = 500, message = "Device token must not exceed 500 characters")
    private String deviceToken;

    @NotNull(message = "Device type is required")
    private DeviceType deviceType;

    @Size(max = 50, message = "App version must not exceed 50 characters")
    private String appVersion;
}
