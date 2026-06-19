package com.LastBite.modules.promotion.repository;

import com.LastBite.modules.promotion.entity.VoucherRedemption;
import com.LastBite.modules.promotion.enums.VoucherRedemptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface VoucherRedemptionRepository extends JpaRepository<VoucherRedemption, UUID> {

    @EntityGraph(attributePaths = {"campaign", "code", "userVoucher"})
    Optional<VoucherRedemption> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"campaign", "code", "userVoucher", "order"})
    @Query("SELECT r FROM VoucherRedemption r WHERE r.order.id = :orderId")
    Optional<VoucherRedemption> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    @EntityGraph(attributePaths = {"campaign", "code", "userVoucher", "order", "user"})
    @Query("""
        SELECT r FROM VoucherRedemption r
        WHERE (:campaignId IS NULL OR r.campaign.id = :campaignId)
          AND (:status IS NULL OR r.status = :status)
    """)
    Page<VoucherRedemption> searchAdmin(@Param("campaignId") UUID campaignId,
                                        @Param("status") VoucherRedemptionStatus status,
                                        Pageable pageable);

    @EntityGraph(attributePaths = {"campaign", "code", "userVoucher", "order", "user"})
    @Query("""
        SELECT r FROM VoucherRedemption r
        WHERE r.order.store.id = :storeId
          AND (:campaignId IS NULL OR r.campaign.id = :campaignId)
          AND (:status IS NULL OR r.status = :status)
    """)
    Page<VoucherRedemption> searchMerchant(@Param("storeId") UUID storeId,
                                           @Param("campaignId") UUID campaignId,
                                           @Param("status") VoucherRedemptionStatus status,
                                           Pageable pageable);

    @Query("""
        SELECT COUNT(r) FROM VoucherRedemption r
        WHERE r.user.id = :userId
          AND r.campaign.id = :campaignId
          AND r.status IN :statuses
    """)
    long countUserCampaignUsage(@Param("userId") UUID userId,
                                @Param("campaignId") UUID campaignId,
                                @Param("statuses") Collection<VoucherRedemptionStatus> statuses);

    @Query("""
        SELECT COUNT(r), COALESCE(SUM(r.discountAmount), 0),
               COALESCE(SUM(r.platformFundedAmount), 0),
               COALESCE(SUM(r.merchantFundedAmount), 0)
        FROM VoucherRedemption r
        WHERE r.campaign.id = :campaignId
          AND r.status = com.LastBite.modules.promotion.enums.VoucherRedemptionStatus.REDEEMED
    """)
    Object[] analytics(@Param("campaignId") UUID campaignId);

    @Query("""
        SELECT COALESCE(SUM(r.platformFundedAmount), 0)
        FROM VoucherRedemption r
        WHERE r.order.id = :orderId
          AND r.status IN :statuses
    """)
    BigDecimal sumPlatformFundingByOrder(@Param("orderId") UUID orderId,
                                         @Param("statuses") Collection<VoucherRedemptionStatus> statuses);
}
