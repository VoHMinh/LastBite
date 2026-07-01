package com.LastBite.modules.bag.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.analytics.service.StoreEngagementAnalyticsService;
import com.LastBite.modules.bag.dto.response.PublicBagDetailResponse;
import com.LastBite.modules.bag.dto.response.PublicBagSummaryResponse;
import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.bag.service.BagDiscoveryServicePort;
import com.LastBite.modules.store.enums.StoreCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bags")
@RequiredArgsConstructor
@Tag(name = "Public Bags", description = "Discovery và xem chi tiết túi hôm nay")
public class BagPublicController {

    private final BagDiscoveryServicePort discoveryService;
    private final StoreEngagementAnalyticsService analyticsService;

    @GetMapping("/today")
    @Operation(operationId = "getTodayBags", summary = "Lấy danh sách túi hôm nay trong bán kính X km")
    public ResponseEntity<ApiResponse<List<PublicBagSummaryResponse>>> today(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false, defaultValue = "5") Double radius,
            @RequestParam(required = false) DietType dietType,
            @RequestParam(required = false) BagType bagType,
            @RequestParam(defaultValue = "pickup_time") String sort,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok(discoveryService.today(
                extractUserId(jwt), lat, lng, radius, dietType, bagType, sort, limit)));
    }

    @GetMapping("/nearby")
    @Operation(operationId = "getNearbyBags", summary = "Tìm túi gần khách hoặc fallback theo quận")
    public ResponseEntity<ApiResponse<List<PublicBagSummaryResponse>>> nearby(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false, defaultValue = "5") Double radius,
            @RequestParam(required = false) StoreCategory category,
            @RequestParam(required = false) DietType dietType,
            @RequestParam(required = false) BagType bagType,
            @RequestParam(required = false) String district,
            @RequestParam(defaultValue = "pickup_time") String sort,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok(
                discoveryService.discover(
                        extractUserId(jwt), lat, lng, radius, category, dietType, bagType, district, sort, limit)));
    }

    @GetMapping("/search")
    @Operation(operationId = "searchBags", summary = "Tim kiem tui voi ranking relevance")
    public ResponseEntity<ApiResponse<List<PublicBagSummaryResponse>>> search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false, defaultValue = "5") Double radius,
            @RequestParam(required = false) StoreCategory category,
            @RequestParam(required = false) DietType dietType,
            @RequestParam(required = false) BagType bagType,
            @RequestParam(required = false) String district,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok(
                discoveryService.search(
                        extractUserId(jwt), q, lat, lng, radius, category, dietType, bagType, district, sort, limit)));
    }
    @GetMapping("/{bagId}")
    @Operation(operationId = "getBagDetail", summary = "Lấy chi tiết túi hôm nay")
    public ResponseEntity<ApiResponse<PublicBagDetailResponse>> detail(@AuthenticationPrincipal Jwt jwt,
                                                                       @PathVariable UUID bagId,
                                                                       @RequestParam(required = false) String source,
                                                                       HttpServletRequest request) {
        UUID userId = extractUserId(jwt);
        PublicBagDetailResponse response = discoveryService.detail(bagId, userId);
        analyticsService.recordBagViewSafely(response.getStoreId(), response.getBagId(), userId, source, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private UUID extractUserId(Jwt jwt) {
        return jwt == null ? null : UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
