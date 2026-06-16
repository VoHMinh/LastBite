package com.LastBite.modules.merchant.repository;

import com.LastBite.modules.merchant.entity.StoreVersion;
import com.LastBite.modules.merchant.enums.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface StoreVersionRepository extends JpaRepository<StoreVersion, UUID> {
    @Query("select coalesce(max(v.versionNumber), 0) from StoreVersion v where v.store.id = :storeId")
    int maxVersionNumber(UUID storeId);

    Optional<StoreVersion> findTopByStoreIdAndReviewStatusOrderByVersionNumberDesc(
            UUID storeId, ReviewStatus reviewStatus);
}
