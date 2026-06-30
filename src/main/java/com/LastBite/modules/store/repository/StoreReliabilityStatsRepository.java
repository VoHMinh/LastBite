package com.LastBite.modules.store.repository;

import com.LastBite.modules.store.entity.StoreReliabilityStats;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StoreReliabilityStatsRepository extends JpaRepository<StoreReliabilityStats, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StoreReliabilityStats s WHERE s.id = :storeId")
    Optional<StoreReliabilityStats> findByStoreIdForUpdate(@Param("storeId") UUID storeId);
}