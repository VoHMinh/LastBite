package com.LastBite.modules.audit.service;

import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.audit.dto.response.AdminAuditLogResponse;
import com.LastBite.modules.audit.entity.AdminAuditLog;
import com.LastBite.modules.audit.repository.AdminAuditLogRepository;
import com.LastBite.modules.auth.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private final AdminAuditLogRepository repository;

    public void record(User actor, String action, String targetType, UUID targetId,
                       String reason, String metadata) {
        repository.save(AdminAuditLog.builder()
                .actor(actor)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .reason(reason)
                .metadata(metadata)
                .build());
    }

    public PageResponse<AdminAuditLogResponse> search(UUID actorId, String action, String targetType, UUID targetId,
                                                      Instant from, Instant to, Pageable pageable) {
        var page = repository.search(actorId, trimToNull(action), trimToNull(targetType), targetId, from, to, pageable)
                .map(this::toResponse);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    private AdminAuditLogResponse toResponse(AdminAuditLog log) {
        User actor = log.getActor();
        return AdminAuditLogResponse.builder()
                .id(log.getId())
                .actorId(actor == null ? null : actor.getId())
                .actorName(actor == null ? null : actor.getFullName())
                .action(log.getAction())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .reason(log.getReason())
                .metadata(log.getMetadata())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
