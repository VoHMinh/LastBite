package com.LastBite.modules.order.service;

import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.audit.dto.response.OrderStatusHistoryResponse;
import com.LastBite.modules.order.dto.request.CreateOrderRequest;
import com.LastBite.modules.order.dto.response.OrderResponse;
import com.LastBite.modules.order.enums.OrderRefundStatus;
import com.LastBite.modules.order.enums.OrderStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OrderServicePort {

    OrderResponse create(UUID userId, CreateOrderRequest request);

    OrderResponse get(UUID userId, UUID orderId);

    OrderResponse cancel(UUID userId, UUID orderId);

    PageResponse<OrderResponse> list(UUID userId, OrderStatus status, OrderRefundStatus refundStatus,
                                      LocalDate pickupDateFrom, LocalDate pickupDateTo, Pageable pageable);

    List<OrderStatusHistoryResponse> timeline(UUID userId, UUID orderId);
}
