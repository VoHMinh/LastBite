package com.LastBite.modules.refund.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.refund.dto.request.MarkRefundTransactionFailedRequest;
import com.LastBite.modules.refund.dto.request.MarkRefundTransactionSucceededRequest;
import com.LastBite.modules.refund.dto.request.ReviewRefundRequest;
import com.LastBite.modules.refund.dto.response.RefundResponse;
import com.LastBite.modules.refund.enums.RefundReason;
import com.LastBite.modules.refund.enums.RefundStatus;
import com.LastBite.modules.refund.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RefundResponse>>> list(
            @RequestParam(required = false) RefundStatus status,
            @RequestParam(required = false) RefundReason reason,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(refundService.listAdmin(status, reason, pageable)));
    }

    @PostMapping("/{refundId}/review")
    public ResponseEntity<ApiResponse<RefundResponse>> review(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID refundId,
            @Valid @RequestBody ReviewRefundRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                refundService.review(extractUserId(jwt), refundId, request),
                "Da cap nhat yeu cau hoan tien"));
    }

    @PostMapping("/{refundId}/retry")
    public ResponseEntity<ApiResponse<RefundResponse>> retry(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID refundId) {
        return ResponseEntity.ok(ApiResponse.ok(
                refundService.retry(extractUserId(jwt), refundId),
                "Da dua refund vao hang doi retry"));
    }

    @PostMapping("/transactions/{transactionId}/mark-succeeded")
    public ResponseEntity<ApiResponse<RefundResponse>> markSucceeded(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID transactionId,
            @Valid @RequestBody MarkRefundTransactionSucceededRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                refundService.markTransactionSucceeded(extractUserId(jwt), transactionId,
                        request.getProviderReference(), request.getNote()),
                "Da danh dau giao dich hoan tien thanh cong"));
    }

    @PostMapping("/transactions/{transactionId}/mark-failed")
    public ResponseEntity<ApiResponse<RefundResponse>> markFailed(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID transactionId,
            @Valid @RequestBody MarkRefundTransactionFailedRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                refundService.markTransactionFailed(extractUserId(jwt), transactionId, request.getFailureReason()),
                "Da danh dau giao dich hoan tien that bai"));
    }

    private UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
