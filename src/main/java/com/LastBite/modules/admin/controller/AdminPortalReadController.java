package com.LastBite.modules.admin.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.admin.dto.AdminDtos.*;
import com.LastBite.modules.admin.service.AdminReadService;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.enums.UserStatus;
import com.LastBite.modules.merchant.enums.ReviewStatus;
import com.LastBite.modules.order.enums.OrderRefundStatus;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.payment.enums.PaymentStatus;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.store.enums.StoreStatus;
import com.LastBite.modules.store.enums.VerificationStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/portal")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPortalReadController {
    private final AdminReadService adminReadService;

    @GetMapping("/dashboard/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> dashboardSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "Asia/Ho_Chi_Minh") String timezone) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.dashboardSummary(date, timezone)));
    }

    @GetMapping("/dashboard/action-queue")
    public ResponseEntity<ApiResponse<ActionQueueResponse>> actionQueue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "Asia/Ho_Chi_Minh") String timezone) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.actionQueue(date, timezone)));
    }

    @GetMapping("/analytics/sales")
    public ResponseEntity<ApiResponse<TimeSeriesResponse>> sales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "Asia/Ho_Chi_Minh") String timezone) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.salesSeries(from, to, timezone)));
    }

    @GetMapping("/analytics/funnel")
    public ResponseEntity<ApiResponse<FunnelResponse>> funnel(@RequestParam(required = false) Instant from,
                                                              @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.funnel(from, to)));
    }

    @GetMapping("/analytics/categories")
    public ResponseEntity<ApiResponse<List<CategoryMetric>>> categories(@RequestParam(required = false) Instant from,
                                                                        @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.categoryMetrics(from, to)));
    }

    @GetMapping("/analytics/top-stores")
    public ResponseEntity<ApiResponse<List<TopStoreMetric>>> topStores(@RequestParam(required = false) Instant from,
                                                                       @RequestParam(required = false) Instant to,
                                                                       @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.topStores(from, to, limit)));
    }

    @GetMapping("/analytics/refunds")
    public ResponseEntity<ApiResponse<RefundAnalyticsResponse>> refunds(@RequestParam(required = false) Instant from,
                                                                        @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.refundAnalytics(from, to)));
    }

    @GetMapping("/analytics/users")
    public ResponseEntity<ApiResponse<UserAnalyticsResponse>> usersAnalytics(@RequestParam(required = false) Instant from,
                                                                            @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.userAnalytics(from, to)));
    }

    @GetMapping("/analytics/finance/reconciliation")
    public ResponseEntity<ApiResponse<FinanceReconciliationResponse>> finance(@RequestParam(required = false) Instant from,
                                                                              @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.financeReconciliation(from, to)));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<PageResponse<AdminOrderListItem>>> orders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) OrderRefundStatus refundStatus,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.searchOrders(status, paymentStatus, refundStatus, storeId, customerId, from, to, keyword, pageable(page, size))));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<AdminOrderDetail>> orderDetail(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.orderDetail(orderId)));
    }

    @PostMapping("/orders/{orderId}/notes")
    public ResponseEntity<ApiResponse<AdminNoteResponse>> addOrderNote(@AuthenticationPrincipal Jwt jwt,
                                                                       @PathVariable UUID orderId,
                                                                       @Valid @RequestBody AdminNoteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.addNote("ORDER", orderId, userId(jwt), request.note())));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserListItem>>> users(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.searchUsers(role, status, keyword, from, to, pageable(page, size))));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<AdminUserListItem>> userDetail(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.userDetail(userId)));
    }

    @GetMapping("/users/{userId}/activity")
    public ResponseEntity<ApiResponse<Map<String, Object>>> userActivity(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.userActivity(userId)));
    }

    @GetMapping("/merchants")
    public ResponseEntity<ApiResponse<PageResponse<AdminMerchantListItem>>> merchants(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.searchMerchants(status, keyword, pageable(page, size))));
    }

    @GetMapping("/merchants/{merchantId}")
    public ResponseEntity<ApiResponse<AdminMerchantListItem>> merchantDetail(@PathVariable UUID merchantId) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.merchantDetail(merchantId)));
    }

    @GetMapping("/stores")
    public ResponseEntity<ApiResponse<PageResponse<AdminStoreListItem>>> stores(
            @RequestParam(required = false) StoreStatus status,
            @RequestParam(required = false) VerificationStatus verificationStatus,
            @RequestParam(required = false) StoreCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.searchStores(status, verificationStatus, category, keyword, pageable(page, size))));
    }

    @GetMapping("/stores/{storeId}/reliability")
    public ResponseEntity<ApiResponse<StoreReliabilityResponse>> storeReliability(@PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.storeReliability(storeId)));
    }

    @GetMapping("/stores/reliability/watchlist")
    public ResponseEntity<ApiResponse<List<StoreReliabilityResponse>>> watchlist(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(adminReadService.reliabilityWatchlist(limit)));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
