package com.LastBite.modules.store.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
public class StoreSpecialHoursResponse {
    private UUID id;
    private UUID storeId;
    private LocalDate specialDate;
    private LocalTime openTime;
    private LocalTime closeTime;
    private boolean closed;
    private String reason;
    private UUID createdByUserId;
    private Instant createdAt;
    private Instant updatedAt;
}
