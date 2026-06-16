package com.LastBite.modules.payment.repository;

import com.LastBite.modules.payment.entity.PaymentTransaction;
import com.LastBite.modules.payment.enums.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByProviderAndProviderTransactionId(PaymentProvider provider, String providerTransactionId);
}
