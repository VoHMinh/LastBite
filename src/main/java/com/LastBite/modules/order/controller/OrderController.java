package com.LastBite.modules.order.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.order.dto.request.CreateOrderRequest;
import com.LastBite.modules.order.dto.response.OrderResponse;
import com.LastBite.modules.order.service.OrderServicePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Orders", description = "Order reservation, payment status and customer cancellation")
public class OrderController {

    private final OrderServicePort orderService;

    @PostMapping
    @Operation(summary = "Reserve today's surprise bag and create payment checkout")
    public ResponseEntity<ApiResponse<OrderResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.create(extractUserId(jwt), request),
                "Da giu tui, vui long thanh toan truoc khi het han"));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get my order detail")
    public ResponseEntity<ApiResponse<OrderResponse>> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.get(extractUserId(jwt), orderId)));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel my order")
    public ResponseEntity<ApiResponse<OrderResponse>> cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.cancel(extractUserId(jwt), orderId),
                "Da huy don hang"));
    }

    private UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
