package com.LastBite.modules.store.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.store.dto.request.RejectStoreRequest;
import com.LastBite.modules.store.dto.request.RequestStoreChangesRequest;
import com.LastBite.modules.store.dto.response.StoreDetailResponse;
import com.LastBite.modules.merchant.dto.response.AdminStoreReviewResponse;
import com.LastBite.modules.merchant.service.AdminStoreReviewService;
import com.LastBite.modules.store.enums.VerificationStatus;
import com.LastBite.modules.store.service.StoreServicePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/stores")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Stores", description = "Duyệt hồ sơ cửa hàng")
public class AdminStoreController {

    private final StoreServicePort storeService;
    private final AdminStoreReviewService reviewService;

    @GetMapping
    @Operation(operationId = "listAdminStores", summary = "Danh sách cửa hàng theo trạng thái duyệt")
    public ResponseEntity<ApiResponse<PageResponse<StoreDetailResponse>>> list(
            @RequestParam(defaultValue = "PENDING") VerificationStatus verificationStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(
                storeService.listStoresForReview(verificationStatus, pageable)));
    }

    @GetMapping("/{storeId}")
    @Operation(summary = "Chi tiết hồ sơ cửa hàng cho admin")
    public ResponseEntity<ApiResponse<AdminStoreReviewResponse>> detail(@PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.detail(storeId)));
    }

    @PatchMapping("/{storeId}/approve")
    @Operation(summary = "Duyệt cửa hàng")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> approve(@PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.ok(storeService.approveStore(storeId), "Đã duyệt cửa hàng"));
    }

    @PatchMapping("/{storeId}/reject")
    @Operation(summary = "Từ chối hồ sơ cửa hàng")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> reject(
            @PathVariable UUID storeId,
            @Valid @RequestBody RejectStoreRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                storeService.rejectStore(storeId, request.getRejectionReason()),
                "Đã từ chối hồ sơ cửa hàng"));
    }
    @PatchMapping("/{storeId}/request-changes")
    @Operation(summary = "Yeu cau merchant sua tung muc trong ho so")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> requestChanges(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @Valid @RequestBody RequestStoreChangesRequest request) {
        UUID adminId = UUID.fromString(jwt.getClaimAsString("user_id"));
        return ResponseEntity.ok(ApiResponse.ok(
                storeService.requestChanges(adminId, storeId, request),
                "Da gui yeu cau chinh sua"));
    }
}
