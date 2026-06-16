package com.LastBite.modules.settlement.repository;

import com.LastBite.modules.settlement.entity.PlatformCommission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlatformCommissionRepository extends JpaRepository<PlatformCommission, UUID> {
    boolean existsByOrderId(UUID orderId);
}
