package com.LastBite.modules.settlement.repository;

import com.LastBite.modules.settlement.entity.MerchantSettlement;
import com.LastBite.modules.settlement.enums.MerchantSettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface MerchantSettlementRepository extends JpaRepository<MerchantSettlement, UUID> {
    Page<MerchantSettlement> findAllByBusinessProfileOwnerId(UUID ownerId, Pageable pageable);
    Page<MerchantSettlement> findAllByStatus(MerchantSettlementStatus status, Pageable pageable);
    long countByBusinessProfileOwnerIdAndStatusIn(UUID ownerId, Collection<MerchantSettlementStatus> statuses);
}
