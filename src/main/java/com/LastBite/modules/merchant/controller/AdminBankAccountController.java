package com.LastBite.modules.merchant.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.merchant.dto.request.RejectBankAccountRequest;
import com.LastBite.modules.merchant.dto.response.BankAccountResponse;
import com.LastBite.modules.merchant.enums.BankAccountVerificationStatus;
import com.LastBite.modules.merchant.service.MerchantBankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/bank-accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBankAccountController {

    private final MerchantBankAccountService bankAccountService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BankAccountResponse>>> list(
            @RequestParam(required = false) BankAccountVerificationStatus status,
            @RequestParam(required = false) UUID businessProfileId,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(bankAccountService.listAdmin(status,
                businessProfileId, storeId, pageable)));
    }

    @GetMapping("/{bankAccountId}")
    public ResponseEntity<ApiResponse<BankAccountResponse>> get(@PathVariable UUID bankAccountId) {
        return ResponseEntity.ok(ApiResponse.ok(bankAccountService.getAdmin(bankAccountId)));
    }

    @PatchMapping("/{bankAccountId}/approve")
    public ResponseEntity<ApiResponse<BankAccountResponse>> approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bankAccountId) {
        return ResponseEntity.ok(ApiResponse.ok(bankAccountService.approve(userId(jwt), bankAccountId),
                "Da duyet tai khoan ngan hang"));
    }

    @PatchMapping("/{bankAccountId}/reject")
    public ResponseEntity<ApiResponse<BankAccountResponse>> reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bankAccountId,
            @Valid @RequestBody RejectBankAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(bankAccountService.reject(userId(jwt), bankAccountId, request),
                "Da tu choi tai khoan ngan hang"));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
