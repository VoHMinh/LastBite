package com.LastBite.modules.payment.repository;

import com.LastBite.modules.payment.entity.Payment;
import com.LastBite.modules.payment.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findByProviderOrderCode(Long providerOrderCode);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    List<Payment> findByStatusAndExpiresAtLessThanEqual(PaymentStatus status, Instant expiresAt);
}
