package com.LastBite.modules.merchant.repository;

import com.LastBite.modules.merchant.entity.StoreReviewApplication;
import com.LastBite.modules.merchant.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoreReviewApplicationRepository extends JpaRepository<StoreReviewApplication, UUID> {
    @EntityGraph(attributePaths = {"store", "store.businessProfile", "store.createdBy", "submittedBy"})
    Page<StoreReviewApplication> findAllByStatus(ReviewStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {
            "store", "store.businessProfile", "store.createdBy", "submittedBy", "reviewedBy",
            "businessProfileVersion", "storeVersion"
    })
    Optional<StoreReviewApplication> findTopByStoreIdOrderByCreatedAtDesc(UUID storeId);
}
