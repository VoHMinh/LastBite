package com.LastBite.modules.pickup.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.pickup.dto.request.ConfirmPickupRequest;
import com.LastBite.modules.pickup.dto.response.PickupResponse;
import com.LastBite.modules.pickup.service.PickupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchant/pickups")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MERCHANT_OWNER','MANAGER','STAFF')")
public class MerchantPickupController {

    private final PickupService pickupService;

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<PickupResponse>> confirm(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConfirmPickupRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(pickupService.confirm(extractUserId(jwt), request),
                "Da xac nhan pickup"));
    }

    private UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
