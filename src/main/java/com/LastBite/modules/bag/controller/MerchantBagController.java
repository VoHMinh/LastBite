package com.LastBite.modules.bag.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.bag.dto.request.AdjustTodayStockRequest;
import com.LastBite.modules.bag.dto.request.CreateSurpriseBagRequest;
import com.LastBite.modules.bag.dto.request.SetDailyStockRequest;
import com.LastBite.modules.bag.dto.request.UpdateSurpriseBagRequest;
import com.LastBite.modules.bag.dto.response.DailyStockResponse;
import com.LastBite.modules.bag.dto.response.StockAuditLogResponse;
import com.LastBite.modules.bag.dto.response.SurpriseBagResponse;
import com.LastBite.modules.bag.service.SurpriseBagServicePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchant/bags")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STORE_OWNER')")
@Tag(name = "Merchant Bags", description = "Quản lý túi bất ngờ và tồn kho theo ngày")
public class MerchantBagController {

    private final SurpriseBagServicePort bagService;

    @PostMapping
    @Operation(summary = "Tạo túi bất ngờ")
    public ResponseEntity<ApiResponse<SurpriseBagResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSurpriseBagRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(bagService.create(extractUserId(jwt), request), "Đã tạo túi"));
    }

    @GetMapping
    @Operation(summary = "Danh sách túi của cửa hàng")
    public ResponseEntity<ApiResponse<PageResponse<SurpriseBagResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(bagService.list(extractUserId(jwt), pageable)));
    }

    @PatchMapping("/{bagId}")
    @Operation(summary = "Sửa thông tin túi")
    public ResponseEntity<ApiResponse<SurpriseBagResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bagId,
            @Valid @RequestBody UpdateSurpriseBagRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(bagService.update(extractUserId(jwt), bagId, request), "Đã cập nhật túi"));
    }

    @DeleteMapping("/{bagId}")
    @Operation(summary = "Xóa mềm túi")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bagId) {
        bagService.softDelete(extractUserId(jwt), bagId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã xóa túi"));
    }

    @PatchMapping("/{bagId}/pause")
    @Operation(summary = "Tạm dừng bán túi")
    public ResponseEntity<ApiResponse<SurpriseBagResponse>> pause(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bagId) {
        return ResponseEntity.ok(ApiResponse.ok(bagService.pause(extractUserId(jwt), bagId), "Túi đã tạm dừng"));
    }

    @PatchMapping("/{bagId}/resume")
    @Operation(summary = "Mở bán lại túi")
    public ResponseEntity<ApiResponse<SurpriseBagResponse>> resume(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bagId) {
        return ResponseEntity.ok(ApiResponse.ok(bagService.resume(extractUserId(jwt), bagId), "Túi đã mở bán lại"));
    }

    @PutMapping("/{bagId}/stock/{date}")
    @Operation(summary = "Set số lượng túi theo ngày")
    public ResponseEntity<ApiResponse<DailyStockResponse>> setStock(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bagId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody SetDailyStockRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(bagService.setStock(extractUserId(jwt), bagId, date, request),
                "Đã cập nhật tồn kho"));
    }

    @PatchMapping("/{bagId}/stock/today")
    @Operation(summary = "Điều chỉnh nhanh số lượng hôm nay")
    public ResponseEntity<ApiResponse<DailyStockResponse>> adjustTodayStock(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bagId,
            @Valid @RequestBody AdjustTodayStockRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(bagService.adjustTodayStock(extractUserId(jwt), bagId, request),
                "Đã điều chỉnh tồn kho hôm nay"));
    }

    @GetMapping("/{bagId}/audit-logs")
    @Operation(summary = "Lịch sử thay đổi tồn kho")
    public ResponseEntity<ApiResponse<PageResponse<StockAuditLogResponse>>> auditLogs(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bagId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(bagService.auditLogs(extractUserId(jwt), bagId, pageable)));
    }

    private UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
