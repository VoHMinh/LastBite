package com.LastBite.modules.notification.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.notification.dto.request.RegisterNotificationDeviceRequest;
import com.LastBite.modules.notification.dto.response.NotificationDeviceResponse;
import com.LastBite.modules.notification.service.NotificationDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications/devices")
@RequiredArgsConstructor
@Tag(name = "Notification Devices", description = "Đăng ký và quản lý thiết bị nhận thông báo push")
public class NotificationDeviceController {

    private final NotificationDeviceService deviceService;

    @PostMapping
    @Operation(operationId = "registerNotificationDevice", summary = "Đăng ký thiết bị nhận push notification")
    public ResponseEntity<ApiResponse<NotificationDeviceResponse>> register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RegisterNotificationDeviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(deviceService.register(extractUserId(jwt), request),
                        "Device token registered"));
    }

    @GetMapping
    @Operation(operationId = "listNotificationDevices", summary = "Lấy danh sách thiết bị đã đăng ký của user")
    public ResponseEntity<ApiResponse<List<NotificationDeviceResponse>>> list(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(deviceService.listActive(extractUserId(jwt))));
    }

    @DeleteMapping("/{deviceId}")
    @Operation(operationId = "deactivateNotificationDevice", summary = "Hủy đăng ký một thiết bị")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID deviceId) {
        deviceService.deactivate(extractUserId(jwt), deviceId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping
    @Operation(operationId = "deactivateAllNotificationDevices", summary = "Hủy đăng ký tất cả thiết bị của user")
    public ResponseEntity<ApiResponse<Void>> deactivateAll(@AuthenticationPrincipal Jwt jwt) {
        deviceService.deactivateAll(extractUserId(jwt));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    private UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
