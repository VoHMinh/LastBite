package com.LastBite.modules.notification.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.notification.dto.request.UpdateNotificationPreferenceRequest;
import com.LastBite.modules.notification.dto.response.NotificationPreferenceResponse;
import com.LastBite.modules.notification.service.NotificationPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications/preferences")
@RequiredArgsConstructor
@Tag(name = "Notification Preferences", description = "Quản lý tùy chọn thông báo của người dùng")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationPreferenceResponse>>> list(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(preferenceService.list(extractUserId(jwt))));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateNotificationPreferenceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(preferenceService.update(extractUserId(jwt), request)));
    }

    private UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
