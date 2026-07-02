package com.LastBite.modules.notification.repository;

import com.LastBite.modules.notification.entity.NotificationPreference;
import com.LastBite.modules.notification.enums.NotificationCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    List<NotificationPreference> findByUserId(UUID userId);

    Optional<NotificationPreference> findByUserIdAndCategory(UUID userId, NotificationCategory category);

    long deleteByUserId(UUID userId);
}
