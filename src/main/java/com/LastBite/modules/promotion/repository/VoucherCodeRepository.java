package com.LastBite.modules.promotion.repository;

import com.LastBite.modules.promotion.entity.VoucherCode;
import com.LastBite.modules.promotion.enums.VoucherCodeStatus;
import com.LastBite.modules.promotion.enums.VoucherCodeType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoucherCodeRepository extends JpaRepository<VoucherCode, UUID> {

    @EntityGraph(attributePaths = {"campaign", "campaign.store", "campaign.bag"})
    Optional<VoucherCode> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"campaign", "campaign.store", "campaign.bag"})
    @Query("SELECT c FROM VoucherCode c WHERE c.code = :code")
    Optional<VoucherCode> findByCodeForUpdate(@Param("code") String code);

    boolean existsByCode(String code);

    @EntityGraph(attributePaths = {"campaign"})
    Page<VoucherCode> findAllByCampaignId(UUID campaignId, Pageable pageable);

    @EntityGraph(attributePaths = {"campaign", "campaign.store", "campaign.bag"})
    @Query("""
        SELECT c FROM VoucherCode c
        WHERE c.campaign.id IN :campaignIds
          AND c.codeType = :codeType
          AND c.status = :status
          AND (c.startsAt IS NULL OR c.startsAt <= :now)
          AND (c.endsAt IS NULL OR c.endsAt > :now)
    """)
    List<VoucherCode> findActivePublicCodes(@Param("campaignIds") Collection<UUID> campaignIds,
                                            @Param("codeType") VoucherCodeType codeType,
                                            @Param("status") VoucherCodeStatus status,
                                            @Param("now") Instant now);
}
