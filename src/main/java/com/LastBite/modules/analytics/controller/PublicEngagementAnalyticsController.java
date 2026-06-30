package com.LastBite.modules.analytics.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.analytics.dto.request.TrackEngagementEventRequest;
import com.LastBite.modules.analytics.dto.response.TrackEngagementEventResponse;
import com.LastBite.modules.analytics.service.StoreEngagementAnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class PublicEngagementAnalyticsController {

    private final StoreEngagementAnalyticsService analyticsService;

    @PostMapping("/engagement-events")
    public ResponseEntity<ApiResponse<TrackEngagementEventResponse>> track(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TrackEngagementEventRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.track(userId(jwt), request, httpRequest)));
    }

    private UUID userId(Jwt jwt) {
        return jwt == null ? null : UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
