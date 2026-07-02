package com.LastBite.modules.settlement.repository;

import com.LastBite.modules.settlement.entity.StorePayout;
import com.LastBite.modules.settlement.enums.PayoutStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface StorePayoutRepository extends JpaRepository<StorePayout, UUID> {
    Page<StorePayout> findAllBySettlementBusinessProfileOwnerId(UUID ownerId, Pageable pageable);
    Optional<StorePayout> findBySettlementId(UUID settlementId);
    long countBySettlementBusinessProfileOwnerIdAndStatusIn(UUID ownerId, Collection<PayoutStatus> statuses);
}
