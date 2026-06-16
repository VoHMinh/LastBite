package com.LastBite.modules.merchant.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.merchant.dto.request.BusinessProfileRequest;
import com.LastBite.modules.merchant.dto.response.BusinessProfileResponse;
import com.LastBite.modules.merchant.service.MerchantBusinessProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchant/business-profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MERCHANT_OWNER')")
public class MerchantBusinessProfileController {
    private final MerchantBusinessProfileService service;

    @PostMapping
    public ResponseEntity<ApiResponse<BusinessProfileResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BusinessProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.create(userId(jwt), request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<BusinessProfileResponse>> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(userId(jwt))));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<BusinessProfileResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BusinessProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(userId(jwt), request)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
