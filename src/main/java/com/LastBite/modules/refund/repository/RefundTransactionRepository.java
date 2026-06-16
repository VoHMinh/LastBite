package com.LastBite.modules.refund.repository;

import com.LastBite.modules.refund.entity.RefundTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RefundTransactionRepository extends JpaRepository<RefundTransaction, UUID> {
}
