package com.LastBite.modules.store.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.store.dto.request.*;
import com.LastBite.modules.store.dto.response.StoreDetailResponse;
import com.LastBite.modules.store.service.StoreServicePort;
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
@RequestMapping("/api/v1/merchant/stores")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MERCHANT_OWNER')")
public class MerchantStoreController {
    private final StoreServicePort storeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreDetailResponse>>> list(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(storeService.listMyStores(userId(jwt))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StoreDetailResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateStoreRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(storeService.createStore(userId(jwt), request)));
    }

    @GetMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> detail(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.ok(storeService.getStore(userId(jwt), storeId)));
    }

    @PatchMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @Valid @RequestBody UpdateStoreRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(storeService.updateStore(userId(jwt), storeId, request)));
    }

    @PutMapping("/{storeId}/schedules")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> schedules(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @Valid @RequestBody List<ScheduleRequest> request) {
        return ResponseEntity.ok(ApiResponse.ok(storeService.updateSchedules(userId(jwt), storeId, request)));
    }

    @PostMapping("/{storeId}/submit-review")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> submit(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.ok(storeService.submitReview(userId(jwt), storeId)));
    }

    @PatchMapping("/{storeId}/pause")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> pause(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.ok(storeService.pauseStore(userId(jwt), storeId)));
    }

    @PatchMapping("/{storeId}/activate")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> activate(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.ok(storeService.activateStore(userId(jwt), storeId)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
