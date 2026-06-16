package com.LastBite.modules.refund.repository;

import com.LastBite.modules.refund.entity.RefundRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {
    List<RefundRequest> findByOrderId(UUID orderId);
    boolean existsByOrderId(UUID orderId);
}
