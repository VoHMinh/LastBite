package com.LastBite.modules.refund.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.refund.dto.request.ReviewRefundRequest;
import com.LastBite.modules.refund.dto.response.RefundResponse;
import com.LastBite.modules.refund.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/refunds")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRefundController {

    private final RefundService refundService;

    @PostMapping("/{refundId}/review")
    public ResponseEntity<ApiResponse<RefundResponse>> review(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID refundId,
            @Valid @RequestBody ReviewRefundRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                refundService.review(extractUserId(jwt), refundId, request),
                "Da cap nhat yeu cau hoan tien"));
    }

    private UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
