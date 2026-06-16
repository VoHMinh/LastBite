package com.LastBite.modules.settlement.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.settlement.dto.response.MerchantPayableBalanceResponse;
import com.LastBite.modules.settlement.dto.response.PayoutResponse;
import com.LastBite.modules.settlement.dto.response.SettlementResponse;
import com.LastBite.modules.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchant/settlements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MERCHANT_OWNER')")
public class MerchantSettlementController {

    private final SettlementService settlementService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SettlementResponse>>> settlements(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                settlementService.listMerchant(userId(jwt), PageRequest.of(page, Math.min(size, 100)))));
    }

    @GetMapping("/payouts")
    public ResponseEntity<ApiResponse<Page<PayoutResponse>>> payouts(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                settlementService.listMerchantPayouts(userId(jwt), PageRequest.of(page, Math.min(size, 100)))));
    }

    @GetMapping("/payable-balance")
    public ResponseEntity<ApiResponse<MerchantPayableBalanceResponse>> balance(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(settlementService.payableBalance(userId(jwt))));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
