package com.LastBite.modules.bag.dto.response;

import com.LastBite.modules.bag.enums.StockAuditAction;
import com.LastBite.modules.bag.enums.StockAuditActorType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class StockAuditLogResponse {
    private UUID id;
    private UUID bagId;
    private UUID dailyStockId;
    private LocalDate stockDate;
    private UUID actorId;
    private String actorEmail;
    private StockAuditActorType actorType;
    private StockAuditAction action;
    private int delta;
    private int quantityBefore;
    private int quantityAfter;
    private String reason;
    private UUID orderId;
    private Instant createdAt;
}
