package com.LastBite.modules.notification.repository;

import com.LastBite.modules.notification.entity.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {
}
