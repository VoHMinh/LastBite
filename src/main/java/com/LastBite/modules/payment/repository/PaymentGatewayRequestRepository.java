package com.LastBite.modules.payment.repository;

import com.LastBite.modules.payment.entity.PaymentGatewayRequest;
import com.LastBite.modules.payment.enums.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentGatewayRequestRepository extends JpaRepository<PaymentGatewayRequest, UUID> {
    Optional<PaymentGatewayRequest> findByProviderAndIdempotencyKey(PaymentProvider provider, String idempotencyKey);
}
