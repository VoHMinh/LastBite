package com.LastBite.modules.order.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.audit.dto.response.OrderStatusHistoryResponse;
import com.LastBite.modules.order.dto.request.MerchantCancelOrderRequest;
import com.LastBite.modules.order.dto.response.MerchantOrderResponse;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.order.service.MerchantOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchant/stores/{storeId}/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MERCHANT_OWNER','MANAGER','STAFF')")
public class MerchantOrderController {

    private final MerchantOrderService merchantOrderService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MerchantOrderResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.ASC, "pickupStartTime").and(Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.ok(merchantOrderService.list(userId(jwt), storeId,
                date, status, pageable)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<MerchantOrderResponse>> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.ok(merchantOrderService.get(userId(jwt), storeId, orderId)));
    }

    @PostMapping("/{orderId}/ready")
    public ResponseEntity<ApiResponse<MerchantOrderResponse>> markReady(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.ok(merchantOrderService.markReady(userId(jwt), storeId, orderId),
                "Don hang da san sang de pickup"));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<MerchantOrderResponse>> cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @PathVariable UUID orderId,
            @Valid @RequestBody MerchantCancelOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(merchantOrderService.cancel(userId(jwt), storeId, orderId, request),
                "Da huy don hang"));
    }

    @GetMapping("/{orderId}/timeline")
    public ResponseEntity<ApiResponse<List<OrderStatusHistoryResponse>>> timeline(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.ok(merchantOrderService.timeline(userId(jwt), storeId, orderId)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
