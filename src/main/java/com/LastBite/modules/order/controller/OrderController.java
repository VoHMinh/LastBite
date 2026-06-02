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
@Tag(name = "Orders", description = "Đặt giữ túi và snapshot giá tại thời điểm đặt")
public class OrderController {

    private final OrderServicePort orderService;

    @PostMapping
    @Operation(summary = "Đặt giữ túi hôm nay")
    public ResponseEntity<ApiResponse<OrderResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.create(extractUserId(jwt), request),
                "Đã giữ túi, vui lòng thanh toán trước khi hết hạn"));
    }

    private UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
