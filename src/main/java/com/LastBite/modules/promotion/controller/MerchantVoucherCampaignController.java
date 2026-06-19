package com.LastBite.modules.promotion.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.promotion.dto.request.VoucherCampaignUpsertRequest;
import com.LastBite.modules.promotion.dto.request.AddVoucherCodesRequest;
import com.LastBite.modules.promotion.dto.response.VoucherCampaignResponse;
import com.LastBite.modules.promotion.dto.response.VoucherRedemptionResponse;
import com.LastBite.modules.promotion.enums.VoucherCampaignStatus;
import com.LastBite.modules.promotion.enums.VoucherRedemptionStatus;
import com.LastBite.modules.promotion.service.VoucherCampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchant/stores/{storeId}")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MERCHANT_OWNER','MANAGER')")
public class MerchantVoucherCampaignController {

    private final VoucherCampaignService campaignService;

    @GetMapping("/voucher-campaigns")
    public ResponseEntity<ApiResponse<PageResponse<VoucherCampaignResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @RequestParam(required = false) VoucherCampaignStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(campaignService.listMerchant(userId(jwt), storeId, status, pageable)));
    }

    @PostMapping("/voucher-campaigns")
    public ResponseEntity<ApiResponse<VoucherCampaignResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @Valid @RequestBody VoucherCampaignUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.createMerchant(userId(jwt), storeId, request),
                "Da tao voucher campaign"));
    }

    @PatchMapping("/voucher-campaigns/{campaignId}")
    public ResponseEntity<ApiResponse<VoucherCampaignResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @PathVariable UUID campaignId,
            @Valid @RequestBody VoucherCampaignUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.updateMerchant(userId(jwt), storeId, campaignId, request),
                "Da cap nhat voucher campaign"));
    }

    @PostMapping("/voucher-campaigns/{campaignId}/publish")
    public ResponseEntity<ApiResponse<VoucherCampaignResponse>> publish(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @PathVariable UUID campaignId) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.publishMerchant(userId(jwt), storeId, campaignId),
                "Da publish hoac submit voucher campaign"));
    }

    @PostMapping("/voucher-campaigns/{campaignId}/pause")
    public ResponseEntity<ApiResponse<VoucherCampaignResponse>> pause(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @PathVariable UUID campaignId) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.pauseMerchant(userId(jwt), storeId, campaignId),
                "Da pause voucher campaign"));
    }

    @PostMapping("/voucher-campaigns/{campaignId}/end")
    public ResponseEntity<ApiResponse<VoucherCampaignResponse>> end(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @PathVariable UUID campaignId) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.endMerchant(userId(jwt), storeId, campaignId),
                "Da end voucher campaign"));
    }

    @PostMapping("/voucher-campaigns/{campaignId}/codes")
    public ResponseEntity<ApiResponse<List<String>>> addCodes(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @PathVariable UUID campaignId,
            @Valid @RequestBody AddVoucherCodesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.addMerchantCodes(
                userId(jwt), storeId, campaignId, request), "Da them code vao voucher campaign"));
    }
    @GetMapping("/voucher-redemptions")
    public ResponseEntity<ApiResponse<PageResponse<VoucherRedemptionResponse>>> redemptions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(required = false) VoucherRedemptionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(campaignService.listMerchantRedemptions(
                userId(jwt), storeId, campaignId, status, pageable)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
