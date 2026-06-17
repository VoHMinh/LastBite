package com.LastBite.modules.audit.dto.response;

import com.LastBite.modules.audit.enums.AuditActorType;
import com.LastBite.modules.order.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class OrderStatusHistoryResponse {
    private UUID id;
    private UUID orderId;
    private OrderStatus fromStatus;
    private OrderStatus toStatus;
    private AuditActorType actorType;
    private UUID actorUserId;
    private String actorName;
    private String reason;
    private String metadata;
    private Instant createdAt;
}
