package com.LastBite.modules.notification.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.notification.dto.request.RegisterNotificationDeviceRequest;
import com.LastBite.modules.notification.dto.response.NotificationDeviceResponse;
import com.LastBite.modules.notification.service.NotificationDeviceService;
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
public class NotificationDeviceController {

    private final NotificationDeviceService deviceService;

    @PostMapping
    public ResponseEntity<ApiResponse<NotificationDeviceResponse>> register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RegisterNotificationDeviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(deviceService.register(extractUserId(jwt), request),
                        "Device token registered"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationDeviceResponse>>> list(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(deviceService.listActive(extractUserId(jwt))));
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID deviceId) {
        deviceService.deactivate(extractUserId(jwt), deviceId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deactivateAll(@AuthenticationPrincipal Jwt jwt) {
        deviceService.deactivateAll(extractUserId(jwt));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    private UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
