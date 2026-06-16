package com.LastBite.modules.store.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.store.dto.request.CreateStoreRequest;
import com.LastBite.modules.store.dto.request.ScheduleRequest;
import com.LastBite.modules.store.dto.request.UpdateStoreRequest;
import com.LastBite.modules.store.dto.response.StoreDetailResponse;
import com.LastBite.modules.store.service.StoreServicePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoint quản lý cửa hàng — yêu cầu role STORE_OWNER.
 * <p>
 * Lưu ý: Cửa hàng được tạo trong lúc đăng ký đối tác tại
 * {@code POST /api/v1/auth/register-partner}. Controller này
 * chỉ xử lý việc xem và cập nhật cửa hàng đã tồn tại.
 */
@RestController
@RequestMapping("/api/v1/store-owner/store")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MERCHANT_OWNER')")
@Tag(name = "Store Owner", description = "Quản lý cửa hàng (dành cho chủ cửa hàng đã đăng ký)")
public class StoreOwnerController {

    private final StoreServicePort storeService;

    @PostMapping
    @Operation(summary = "Tạo hồ sơ cửa hàng sau khi đăng ký merchant")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> createStore(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateStoreRequest request) {
        UUID ownerId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(storeService.createStore(ownerId, request), "Đã tạo cửa hàng"));
    }

    @GetMapping
    @Operation(summary = "Xem cửa hàng của tôi")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> getMyStore(@AuthenticationPrincipal Jwt jwt) {
        UUID ownerId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(storeService.getMyStore(ownerId)));
    }

    @PutMapping
    @Operation(summary = "Cập nhật thông tin cửa hàng")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> updateStore(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateStoreRequest request) {
        UUID ownerId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(storeService.updateStore(ownerId, request), "Cập nhật thành công"));
    }

    @PutMapping("/schedules")
    @Operation(summary = "Cập nhật lịch mở/đóng cửa")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> updateSchedules(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody List<ScheduleRequest> requests) {
        UUID ownerId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(storeService.updateSchedules(ownerId, requests)));
    }

    @PatchMapping("/pause")
    @Operation(summary = "Tạm ngưng cửa hàng")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> pauseStore(@AuthenticationPrincipal Jwt jwt) {
        UUID ownerId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(storeService.pauseStore(ownerId), "Cửa hàng đã tạm ngưng"));
    }

    @PatchMapping("/activate")
    @Operation(summary = "Mở lại cửa hàng")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> activateStore(@AuthenticationPrincipal Jwt jwt) {
        UUID ownerId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(storeService.activateStore(ownerId), "Cửa hàng đã hoạt động lại"));
    }

    @PostMapping("/submit-review")
    @Operation(summary = "Gửi hồ sơ cửa hàng cho admin duyệt")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> submitReview(@AuthenticationPrincipal Jwt jwt) {
        UUID ownerId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(storeService.submitReview(ownerId), "Đã gửi hồ sơ chờ duyệt"));
    }

    private UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
