package com.LastBite.common.email;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailDeliveryLogRepository extends JpaRepository<EmailDeliveryLog, UUID> {

    Optional<EmailDeliveryLog> findFirstByProviderMessageIdOrderByCreatedAtDesc(String providerMessageId);
}
