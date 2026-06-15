package com.LastBite.modules.merchant.repository;

import com.LastBite.modules.merchant.entity.MerchantBusinessProfileVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;
import java.util.Optional;
import com.LastBite.modules.merchant.enums.ReviewStatus;

public interface MerchantBusinessProfileVersionRepository extends JpaRepository<MerchantBusinessProfileVersion, UUID> {
    @Query("select coalesce(max(v.versionNumber), 0) from MerchantBusinessProfileVersion v where v.businessProfile.id = :profileId")
    int maxVersionNumber(UUID profileId);

    Optional<MerchantBusinessProfileVersion>
    findTopByBusinessProfileIdAndReviewStatusOrderByVersionNumberDesc(
            UUID profileId, ReviewStatus reviewStatus);
}
