package com.LastBite.modules.notification.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.notification.dto.request.BroadcastNotificationRequest;
import com.LastBite.modules.notification.dto.response.BroadcastNotificationResponse;
import com.LastBite.modules.notification.dto.response.NotificationResponse;
import com.LastBite.modules.notification.dto.response.NotificationStatsResponse;
import com.LastBite.modules.notification.service.NotificationInboxService;
import com.LastBite.modules.notification.service.NotificationServicePort;
import com.LastBite.modules.notification.service.PushNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationInboxService inboxService;
    private final NotificationServicePort notificationService;
    private final PushNotificationService pushNotificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(inboxService.list(extractUserId(jwt), page, size)));
    }

    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> unread(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(inboxService.unread(extractUserId(jwt))));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(inboxService.unreadCount(extractUserId(jwt))));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID notificationId) {
        return ResponseEntity.ok(ApiResponse.ok(inboxService.markRead(extractUserId(jwt), notificationId)));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Integer>> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(inboxService.markAllRead(extractUserId(jwt))));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID notificationId) {
        inboxService.delete(extractUserId(jwt), notificationId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/admin/broadcast")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BroadcastNotificationResponse>> broadcast(
            @Valid @RequestBody BroadcastNotificationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.broadcastToCustomers(request),
                "Broadcast notification queued"));
    }

    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<NotificationStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(NotificationStatsResponse.builder()
                .activeDeviceTokens(pushNotificationService.countActiveTokens())
                .fcmAvailable(pushNotificationService.isFcmAvailable())
                .build()));
    }

    private UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
