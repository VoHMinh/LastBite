package com.LastBite.modules.bag.controller;

import com.LastBite.common.response.*;
import com.LastBite.modules.bag.dto.request.CreateSurpriseBagRequest;
import com.LastBite.modules.bag.dto.response.SurpriseBagResponse;
import com.LastBite.modules.bag.service.SurpriseBagServicePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchant/stores/{storeId}/bags")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MERCHANT_OWNER','MANAGER','STAFF')")
public class StoreBagController {
    private final SurpriseBagServicePort bagService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MERCHANT_OWNER','MANAGER')")
    public ResponseEntity<ApiResponse<SurpriseBagResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @Valid @RequestBody CreateSurpriseBagRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(bagService.create(userId(jwt), storeId, request)));
    }

    @GetMapping
    @Operation(operationId = "listStoreBags")
    public ResponseEntity<ApiResponse<PageResponse<SurpriseBagResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.ok(bagService.list(userId(jwt), storeId, pageable)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
