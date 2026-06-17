package com.LastBite.modules.refund.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.refund.dto.request.CreateRefundRequest;
import com.LastBite.modules.refund.dto.request.RefundDestinationRequest;
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
@RequestMapping("/api/v1/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PostMapping("/{orderId}/request")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<RefundResponse>> request(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateRefundRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                refundService.requestCustomerRefund(extractUserId(jwt), orderId, request),
                "Da gui yeu cau hoan tien"));
    }

    @PutMapping("/{refundId}/destination")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<RefundResponse>> updateDestination(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID refundId,
            @Valid @RequestBody RefundDestinationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                refundService.updateDestination(extractUserId(jwt), refundId, request),
                "Da cap nhat tai khoan nhan hoan tien"));
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<RefundResponse>> getByOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.ok(
                refundService.getCustomerRefundByOrder(extractUserId(jwt), orderId)));
    }

    private UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
