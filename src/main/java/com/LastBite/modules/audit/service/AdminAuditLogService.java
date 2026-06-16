package com.LastBite.modules.audit.service;

import com.LastBite.modules.audit.entity.AdminAuditLog;
import com.LastBite.modules.audit.repository.AdminAuditLogRepository;
import com.LastBite.modules.auth.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
