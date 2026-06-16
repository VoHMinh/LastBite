package com.LastBite.modules.order.service;

import com.LastBite.modules.order.dto.request.CreateOrderRequest;
import com.LastBite.modules.order.dto.response.OrderResponse;

import java.util.UUID;

public interface OrderServicePort {

    OrderResponse create(UUID userId, CreateOrderRequest request);

    OrderResponse get(UUID userId, UUID orderId);

    OrderResponse cancel(UUID userId, UUID orderId);
}
