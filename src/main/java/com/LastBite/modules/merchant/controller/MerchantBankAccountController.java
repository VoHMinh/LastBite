package com.LastBite.modules.merchant.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.merchant.dto.request.BankAccountRequest;
import com.LastBite.modules.merchant.dto.response.BankAccountResponse;
import com.LastBite.modules.merchant.service.MerchantBankAccountService;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(operationId = "listMerchantBankAccounts", summary = "Lấy danh sách tài khoản ngân hàng của merchant")
    public ResponseEntity<ApiResponse<List<BankAccountResponse>>> list(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(userId(jwt))));
    }

    @PostMapping
    @Operation(operationId = "createMerchantBankAccount", summary = "Tạo tài khoản ngân hàng mới")
    public ResponseEntity<ApiResponse<BankAccountResponse>> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BankAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.create(userId(jwt), request)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
