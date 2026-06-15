package com.LastBite.modules.merchant.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.merchant.dto.request.BankAccountRequest;
import com.LastBite.modules.merchant.dto.response.BankAccountResponse;
import com.LastBite.modules.merchant.service.MerchantBankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/merchant/bank-accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MERCHANT_OWNER')")
public class MerchantBankAccountController {
    private final MerchantBankAccountService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BankAccountResponse>>> list(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(userId(jwt))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BankAccountResponse>> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BankAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.create(userId(jwt), request)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
