package com.LastBite.modules.promotion.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.promotion.dto.request.AddVoucherCodesRequest;
import com.LastBite.modules.promotion.dto.request.VoucherCampaignUpsertRequest;
import com.LastBite.modules.promotion.dto.response.VoucherCampaignAnalyticsResponse;
import com.LastBite.modules.promotion.dto.response.VoucherCampaignResponse;
import com.LastBite.modules.promotion.enums.VoucherCampaignOwnerType;
import com.LastBite.modules.promotion.enums.VoucherCampaignStatus;
import com.LastBite.modules.promotion.enums.VoucherFundingSource;
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
@RequestMapping("/api/v1/admin/voucher-campaigns")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminVoucherCampaignController {

    private final VoucherCampaignService campaignService;

    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(operationId = "listAdminVoucherCampaigns")
    public ResponseEntity<ApiResponse<PageResponse<VoucherCampaignResponse>>> list(
            @RequestParam(required = false) VoucherCampaignStatus status,
            @RequestParam(required = false) VoucherCampaignOwnerType ownerType,
            @RequestParam(required = false) VoucherFundingSource fundingSource,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(campaignService.listAdmin(status, ownerType, fundingSource, pageable)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VoucherCampaignResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody VoucherCampaignUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.createAdmin(userId(jwt), request),
                "Da tao voucher campaign"));
    }

    @PatchMapping("/{campaignId}")
    public ResponseEntity<ApiResponse<VoucherCampaignResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID campaignId,
            @Valid @RequestBody VoucherCampaignUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.updateAdmin(userId(jwt), campaignId, request),
                "Da cap nhat voucher campaign"));
    }

    @PostMapping("/{campaignId}/approve")
    public ResponseEntity<ApiResponse<VoucherCampaignResponse>> approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID campaignId) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.approve(userId(jwt), campaignId),
                "Da approve voucher campaign"));
    }

    @PostMapping("/{campaignId}/publish")
    public ResponseEntity<ApiResponse<VoucherCampaignResponse>> publish(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID campaignId) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.publishAdmin(userId(jwt), campaignId),
                "Da publish voucher campaign"));
    }

    @PostMapping("/{campaignId}/pause")
    public ResponseEntity<ApiResponse<VoucherCampaignResponse>> pause(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID campaignId) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.pauseAdmin(userId(jwt), campaignId),
                "Da pause voucher campaign"));
    }

    @PostMapping("/{campaignId}/end")
    public ResponseEntity<ApiResponse<VoucherCampaignResponse>> end(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID campaignId) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.endAdmin(userId(jwt), campaignId),
                "Da end voucher campaign"));
    }

    @PostMapping("/{campaignId}/codes")
    public ResponseEntity<ApiResponse<List<String>>> addCodes(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID campaignId,
            @Valid @RequestBody AddVoucherCodesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.addCodes(userId(jwt), campaignId, request),
                "Da them code vao campaign"));
    }

    @GetMapping("/{campaignId}/analytics")
    public ResponseEntity<ApiResponse<VoucherCampaignAnalyticsResponse>> analytics(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.analytics(campaignId)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
