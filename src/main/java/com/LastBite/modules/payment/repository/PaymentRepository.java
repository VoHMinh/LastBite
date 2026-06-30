package com.LastBite.modules.payment.repository;

import com.LastBite.modules.payment.entity.Payment;
import com.LastBite.modules.payment.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findByProviderOrderCode(Long providerOrderCode);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    List<Payment> findByStatusAndExpiresAtLessThanEqual(PaymentStatus status, Instant expiresAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p FROM Payment p
        JOIN FETCH p.order o
        WHERE p.id = :paymentId
    """)
    Optional<Payment> findByIdForUpdate(@Param("paymentId") UUID paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p FROM Payment p
        JOIN FETCH p.order o
        WHERE p.order.id = :orderId
    """)
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p FROM Payment p
        JOIN FETCH p.order o
        WHERE p.providerOrderCode = :providerOrderCode
    """)
    Optional<Payment> findByProviderOrderCodeForUpdate(@Param("providerOrderCode") Long providerOrderCode);
}
