package com.LastBite.modules.bag.repository;

import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.bag.enums.BagStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SurpriseBagRepository extends JpaRepository<SurpriseBag, UUID> {

    @EntityGraph(attributePaths = {"store"})
    Page<SurpriseBag> findByStoreBusinessProfileOwnerIdAndStatusNot(
            UUID ownerId, BagStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"store"})
    Optional<SurpriseBag> findByIdAndStoreBusinessProfileOwnerId(UUID id, UUID ownerId);

    @EntityGraph(attributePaths = {"store"})
    Page<SurpriseBag> findByStoreIdAndStatusNot(UUID storeId, BagStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"store"})
    Optional<SurpriseBag> findByIdAndStoreId(UUID id, UUID storeId);

    @EntityGraph(attributePaths = {"store"})
    List<SurpriseBag> findByStatus(BagStatus status);

    @Query("""
        SELECT b FROM SurpriseBag b
        WHERE b.status = com.LastBite.modules.bag.enums.BagStatus.ACTIVE
          AND NOT EXISTS (
              SELECT s.id FROM BagDailyStock s
              WHERE s.bag.id = b.id AND s.date = :date
          )
    """)
    List<SurpriseBag> findActiveBagsMissingStockForDate(LocalDate date);
}
