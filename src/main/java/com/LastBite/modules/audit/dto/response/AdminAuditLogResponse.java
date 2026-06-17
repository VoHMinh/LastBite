package com.LastBite.modules.audit.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AdminAuditLogResponse {
    private UUID id;
    private UUID actorId;
    private String actorName;
    private String action;
    private String targetType;
    private UUID targetId;
    private String reason;
    private String metadata;
    private Instant createdAt;
}
