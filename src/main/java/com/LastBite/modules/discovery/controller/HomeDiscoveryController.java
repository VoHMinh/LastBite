package com.LastBite.modules.discovery.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.discovery.dto.response.HomeDiscoveryCollectionResponse;
import com.LastBite.modules.discovery.service.HomeDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
@Tag(name = "Home Discovery", description = "Curated collections on the customer home screen")
public class HomeDiscoveryController {

    private final HomeDiscoveryService homeDiscoveryService;

    @GetMapping("/discovery")
    @Operation(summary = "Lay curated collections cho Home")
    public ResponseEntity<ApiResponse<List<HomeDiscoveryCollectionResponse>>> discovery(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false, defaultValue = "5") Double radius) {
        return ResponseEntity.ok(ApiResponse.ok(
                homeDiscoveryService.discover(extractUserId(jwt), lat, lng, radius)));
    }

    private UUID extractUserId(Jwt jwt) {
        return jwt == null ? null : UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
