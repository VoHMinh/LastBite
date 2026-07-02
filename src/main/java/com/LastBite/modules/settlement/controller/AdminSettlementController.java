package com.LastBite.modules.settlement.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.settlement.dto.request.MarkPayoutFailedRequest;
import com.LastBite.modules.settlement.dto.request.MarkPayoutPaidRequest;
import com.LastBite.modules.settlement.dto.response.PayoutResponse;
import com.LastBite.modules.settlement.dto.response.SettlementResponse;
import com.LastBite.modules.settlement.enums.MerchantSettlementStatus;
import com.LastBite.modules.settlement.service.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/settlements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettlementController {

    private final SettlementService settlementService;

    @PostMapping("/draft-weekly")
    public ResponseEntity<ApiResponse<List<SettlementResponse>>> createDrafts(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(
                settlementService.createWeeklyDrafts(userId(jwt)),
                "Da tao settlement draft"));
    }

    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(operationId = "listAdminSettlements")
    public ResponseEntity<ApiResponse<Page<SettlementResponse>>> list(
            @RequestParam(required = false) MerchantSettlementStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                settlementService.listAdmin(status, PageRequest.of(page, Math.min(size, 100)))));
    }

    @PostMapping("/{settlementId}/approve")
    public ResponseEntity<ApiResponse<SettlementResponse>> approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID settlementId) {
        return ResponseEntity.ok(ApiResponse.ok(
                settlementService.approve(userId(jwt), settlementId),
                "Da duyet settlement"));
    }

    @PostMapping("/{settlementId}/payout")
    public ResponseEntity<ApiResponse<PayoutResponse>> payout(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID settlementId) {
        return ResponseEntity.ok(ApiResponse.ok(
                settlementService.startPayout(userId(jwt), settlementId),
                "Da tao payout"));
    }

    @PostMapping("/payouts/{payoutId}/mark-paid")
    public ResponseEntity<ApiResponse<PayoutResponse>> markPaid(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID payoutId,
            @Valid @RequestBody MarkPayoutPaidRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                settlementService.markPayoutPaid(userId(jwt), payoutId, request),
                "Da ghi nhan payout thanh cong"));
    }

    @PostMapping("/payouts/{payoutId}/mark-failed")
    public ResponseEntity<ApiResponse<PayoutResponse>> markFailed(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID payoutId,
            @Valid @RequestBody MarkPayoutFailedRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                settlementService.markPayoutFailed(userId(jwt), payoutId, request),
                "Da ghi nhan payout that bai"));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
