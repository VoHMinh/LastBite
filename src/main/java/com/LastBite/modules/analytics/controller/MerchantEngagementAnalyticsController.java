package com.LastBite.modules.analytics.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.analytics.dto.response.StoreEngagementAnalyticsResponse;
import com.LastBite.modules.analytics.service.StoreEngagementAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchant/stores/{storeId}/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MERCHANT_OWNER','MANAGER','STAFF')")
public class MerchantEngagementAnalyticsController {

    private final StoreEngagementAnalyticsService analyticsService;

    @GetMapping("/engagement")
    public ResponseEntity<ApiResponse<StoreEngagementAnalyticsResponse>> engagement(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "Asia/Ho_Chi_Minh") String timezone) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.merchantAnalytics(
                userId(jwt), storeId, from, to, timezone)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
