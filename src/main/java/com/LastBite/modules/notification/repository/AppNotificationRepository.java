package com.LastBite.modules.notification.repository;

import com.LastBite.modules.notification.entity.AppNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppNotificationRepository extends JpaRepository<AppNotification, UUID> {

    @EntityGraph(attributePaths = {"recipient"})
    Page<AppNotification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    @EntityGraph(attributePaths = {"recipient"})
    List<AppNotification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(UUID recipientId);

    @Query("SELECT n FROM AppNotification n JOIN FETCH n.recipient WHERE n.id = :id")
    Optional<AppNotification> findByIdWithRecipient(@Param("id") UUID id);

    long countByRecipientIdAndReadFalse(UUID recipientId);

    Optional<AppNotification> findByDedupeKey(String dedupeKey);

    @Modifying
    @Query("""
        UPDATE AppNotification n
        SET n.read = true, n.readAt = :readAt
        WHERE n.recipient.id = :recipientId AND n.read = false
    """)
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);
}
