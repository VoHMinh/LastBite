package com.LastBite.modules.promotion.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.promotion.dto.response.VoucherRedemptionResponse;
import com.LastBite.modules.promotion.enums.VoucherRedemptionStatus;
import com.LastBite.modules.promotion.service.VoucherCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/voucher-redemptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminVoucherRedemptionController {

    private final VoucherCampaignService campaignService;

    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(operationId = "listAdminVoucherRedemptions")
    public ResponseEntity<ApiResponse<PageResponse<VoucherRedemptionResponse>>> list(
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(required = false) VoucherRedemptionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(campaignService.listAdminRedemptions(campaignId, status, pageable)));
    }
}
