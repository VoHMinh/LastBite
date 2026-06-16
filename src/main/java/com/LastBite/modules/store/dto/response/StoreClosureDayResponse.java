package com.LastBite.modules.store.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class StoreClosureDayResponse {
    private UUID id;
    private UUID storeId;
    private LocalDate closedDate;
    private String reason;
    private UUID createdByUserId;
    private Instant createdAt;
    private Instant updatedAt;
}
