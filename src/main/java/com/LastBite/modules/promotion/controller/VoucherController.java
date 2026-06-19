package com.LastBite.modules.promotion.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.promotion.dto.request.ClaimVoucherRequest;
import com.LastBite.modules.promotion.dto.request.ValidateVoucherRequest;
import com.LastBite.modules.promotion.dto.response.UserVoucherResponse;
import com.LastBite.modules.promotion.dto.response.VoucherValidationResponse;
import com.LastBite.modules.promotion.service.VoucherApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class VoucherController {

    private final VoucherApplicationService voucherApplicationService;

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<VoucherValidationResponse>>> available(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID bagId,
            @RequestParam(defaultValue = "1") int quantity) {
        return ResponseEntity.ok(ApiResponse.ok(voucherApplicationService.available(userId(jwt), bagId, quantity)));
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<VoucherValidationResponse>> validate(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ValidateVoucherRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(voucherApplicationService.validate(userId(jwt), request)));
    }

    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<UserVoucherResponse>> claim(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ClaimVoucherRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(voucherApplicationService.claim(userId(jwt), request),
                "Da them voucher vao vi"));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
