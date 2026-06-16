package com.LastBite.modules.audit.repository;

import com.LastBite.modules.audit.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {
}
