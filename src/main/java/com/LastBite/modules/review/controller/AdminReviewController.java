package com.LastBite.modules.review.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.review.dto.request.HideReviewRequest;
import com.LastBite.modules.review.dto.request.ResolveReviewReportRequest;
import com.LastBite.modules.review.dto.response.ReviewReportResponse;
import com.LastBite.modules.review.dto.response.ReviewResponse;
import com.LastBite.modules.review.enums.ReviewReportStatus;
import com.LastBite.modules.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewController {

    private final ReviewService reviewService;

    @GetMapping("/reports")
    @io.swagger.v3.oas.annotations.Operation(operationId = "listReviewReports")
    public ResponseEntity<ApiResponse<Page<ReviewReportResponse>>> reports(
            @RequestParam(required = false) ReviewReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                reviewService.listReports(status, PageRequest.of(page, Math.min(size, 100)))));
    }

    @PostMapping("/reports/{reportId}/resolve")
    public ResponseEntity<ApiResponse<ReviewReportResponse>> resolve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reportId,
            @Valid @RequestBody ResolveReviewReportRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                reviewService.resolveReport(userId(jwt), reportId, request),
                "Da xu ly bao cao danh gia"));
    }

    @PostMapping("/{reviewId}/hide")
    public ResponseEntity<ApiResponse<ReviewResponse>> hide(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reviewId,
            @Valid @RequestBody HideReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                reviewService.hide(userId(jwt), reviewId, request.getReason()),
                "Da an danh gia"));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
