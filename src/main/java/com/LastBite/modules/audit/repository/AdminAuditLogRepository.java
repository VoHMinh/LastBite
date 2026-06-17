package com.LastBite.modules.audit.repository;

import com.LastBite.modules.audit.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    @Query(value = """
            SELECT l FROM AdminAuditLog l
            LEFT JOIN FETCH l.actor
            WHERE (:actorId IS NULL OR l.actor.id = :actorId)
              AND (:action IS NULL OR l.action = :action)
              AND (:targetType IS NULL OR l.targetType = :targetType)
              AND (:targetId IS NULL OR l.targetId = :targetId)
              AND (:from IS NULL OR l.createdAt >= :from)
              AND (:to IS NULL OR l.createdAt <= :to)
        """,
        countQuery = """
            SELECT COUNT(l) FROM AdminAuditLog l
            WHERE (:actorId IS NULL OR l.actor.id = :actorId)
              AND (:action IS NULL OR l.action = :action)
              AND (:targetType IS NULL OR l.targetType = :targetType)
              AND (:targetId IS NULL OR l.targetId = :targetId)
              AND (:from IS NULL OR l.createdAt >= :from)
              AND (:to IS NULL OR l.createdAt <= :to)
        """)
    Page<AdminAuditLog> search(@Param("actorId") UUID actorId,
                               @Param("action") String action,
                               @Param("targetType") String targetType,
                               @Param("targetId") UUID targetId,
                               @Param("from") Instant from,
                               @Param("to") Instant to,
                               Pageable pageable);
}
