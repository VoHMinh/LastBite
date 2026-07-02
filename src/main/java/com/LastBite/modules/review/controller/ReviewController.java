package com.LastBite.modules.review.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.review.dto.request.CreateReviewRequest;
import com.LastBite.modules.review.dto.request.ReportReviewRequest;
import com.LastBite.modules.review.dto.response.ReviewReportResponse;
import com.LastBite.modules.review.dto.response.ReviewResponse;
import com.LastBite.modules.review.dto.response.StoreRatingSummaryResponse;
import com.LastBite.modules.review.service.ReviewService;
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
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/api/v1/orders/{orderId}/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                reviewService.create(userId(jwt), orderId, request),
                "Da gui danh gia"));
    }

    @GetMapping("/api/v1/stores/{storeId}/reviews")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> listStoreReviews(@PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.listStoreReviews(storeId)));
    }

    @GetMapping("/api/v1/bags/{bagId}/reviews")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getBagReviews(@PathVariable UUID bagId) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getBagReviews(bagId)));
    }

    @GetMapping("/api/v1/stores/{storeId}/rating-summary")
    public ResponseEntity<ApiResponse<StoreRatingSummaryResponse>> ratingSummary(@PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getStoreRatingSummary(storeId)));
    }

    @PostMapping("/api/v1/reviews/{reviewId}/report")
    public ResponseEntity<ApiResponse<ReviewReportResponse>> report(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReportReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                reviewService.report(userId(jwt), reviewId, request),
                "Da bao cao danh gia"));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
