package com.LastBite.modules.promotion.repository;

import com.LastBite.modules.promotion.entity.UserVoucher;
import com.LastBite.modules.promotion.enums.UserVoucherStatus;
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

public interface UserVoucherRepository extends JpaRepository<UserVoucher, UUID> {

    @EntityGraph(attributePaths = {"campaign", "code", "campaign.store", "campaign.bag"})
    @Query("""
        SELECT uv FROM UserVoucher uv
        WHERE uv.user.id = :userId
          AND (:status IS NULL OR uv.status = :status)
    """)
    Page<UserVoucher> searchWallet(@Param("userId") UUID userId,
                                   @Param("status") UserVoucherStatus status,
                                   Pageable pageable);

    @EntityGraph(attributePaths = {"campaign", "code", "campaign.store", "campaign.bag"})
    Optional<UserVoucher> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"campaign", "code", "campaign.store", "campaign.bag"})
    @Query("SELECT uv FROM UserVoucher uv WHERE uv.id = :id AND uv.user.id = :userId")
    Optional<UserVoucher> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    boolean existsByUserIdAndCodeIdAndStatusIn(UUID userId, UUID codeId, Collection<UserVoucherStatus> statuses);

    @EntityGraph(attributePaths = {"campaign", "code"})
    @Query("""
        SELECT uv FROM UserVoucher uv
        WHERE uv.user.id = :userId
          AND uv.campaign.id IN :campaignIds
          AND uv.status = com.LastBite.modules.promotion.enums.UserVoucherStatus.CLAIMED
          AND (uv.expiresAt IS NULL OR uv.expiresAt > :now)
    """)
    List<UserVoucher> findClaimedForCampaigns(@Param("userId") UUID userId,
                                              @Param("campaignIds") Collection<UUID> campaignIds,
                                              @Param("now") Instant now);
}
