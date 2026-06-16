package com.LastBite.modules.pickup.dto.response;

import com.LastBite.modules.order.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PickupResponse {
    private UUID orderId;
    private OrderStatus status;
    private Instant pickedUpAt;
}
