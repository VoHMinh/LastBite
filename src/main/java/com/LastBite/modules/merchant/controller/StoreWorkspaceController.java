package com.LastBite.modules.merchant.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.merchant.service.StoreWorkspaceService;
import com.LastBite.modules.store.dto.response.StoreDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/store-workspace")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','STAFF')")
public class StoreWorkspaceController {
    private final StoreWorkspaceService service;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> currentStore(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(service.currentStore(
                UUID.fromString(jwt.getClaimAsString("user_id")))));
    }
}
