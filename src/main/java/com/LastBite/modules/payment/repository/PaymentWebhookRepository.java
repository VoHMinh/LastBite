package com.LastBite.modules.payment.repository;

import com.LastBite.modules.payment.entity.PaymentWebhook;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentWebhookRepository extends JpaRepository<PaymentWebhook, UUID> {
    Optional<PaymentWebhook> findByEventKey(String eventKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM PaymentWebhook w WHERE w.eventKey = :eventKey")
    Optional<PaymentWebhook> findByEventKeyForUpdate(@Param("eventKey") String eventKey);
}
