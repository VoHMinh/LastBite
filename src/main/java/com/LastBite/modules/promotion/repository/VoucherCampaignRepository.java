package com.LastBite.modules.promotion.repository;

import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.promotion.entity.VoucherCampaign;
import com.LastBite.modules.promotion.enums.VoucherCampaignOwnerType;
import com.LastBite.modules.promotion.enums.VoucherCampaignStatus;
import com.LastBite.modules.promotion.enums.VoucherFundingSource;
import com.LastBite.modules.store.enums.StoreCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoucherCampaignRepository extends JpaRepository<VoucherCampaign, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT c FROM VoucherCampaign c
        LEFT JOIN FETCH c.store
        LEFT JOIN FETCH c.bag
        WHERE c.id = :id
    """)
    Optional<VoucherCampaign> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"store", "bag", "createdBy", "approvedBy"})
    @Query("""
        SELECT c FROM VoucherCampaign c
        WHERE (:status IS NULL OR c.status = :status)
          AND (:ownerType IS NULL OR c.ownerType = :ownerType)
          AND (:fundingSource IS NULL OR c.fundingSource = :fundingSource)
    """)
    Page<VoucherCampaign> searchAdmin(@Param("status") VoucherCampaignStatus status,
                                      @Param("ownerType") VoucherCampaignOwnerType ownerType,
                                      @Param("fundingSource") VoucherFundingSource fundingSource,
                                      Pageable pageable);

    @EntityGraph(attributePaths = {"store", "bag", "createdBy", "approvedBy"})
    @Query("""
        SELECT c FROM VoucherCampaign c
        WHERE c.store.id = :storeId
          AND (:status IS NULL OR c.status = :status)
    """)
    Page<VoucherCampaign> searchMerchant(@Param("storeId") UUID storeId,
                                         @Param("status") VoucherCampaignStatus status,
                                         Pageable pageable);

    @EntityGraph(attributePaths = {"store", "bag"})
    @Query("""
        SELECT c FROM VoucherCampaign c
        WHERE c.status = com.LastBite.modules.promotion.enums.VoucherCampaignStatus.ACTIVE
          AND c.startsAt <= :now
          AND c.endsAt > :now
          AND (c.store IS NULL OR c.store.id = :storeId)
          AND (c.bag IS NULL OR c.bag.id = :bagId)
          AND (c.category IS NULL OR c.category = :category)
          AND (c.bagType IS NULL OR c.bagType = :bagType)
          AND (c.dietType IS NULL OR c.dietType = :dietType)
    """)
    List<VoucherCampaign> findEligibleForBag(@Param("storeId") UUID storeId,
                                             @Param("bagId") UUID bagId,
                                             @Param("category") StoreCategory category,
                                             @Param("bagType") BagType bagType,
                                             @Param("dietType") DietType dietType,
                                             @Param("now") Instant now);
}
