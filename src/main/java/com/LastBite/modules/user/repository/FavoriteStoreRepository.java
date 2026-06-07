package com.LastBite.modules.user.repository;

import com.LastBite.modules.user.entity.FavoriteStore;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteStoreRepository extends JpaRepository<FavoriteStore, UUID> {

    @EntityGraph(attributePaths = {"store"})
    List<FavoriteStore> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"store"})
    Optional<FavoriteStore> findByUserIdAndStoreId(UUID userId, UUID storeId);

    @EntityGraph(attributePaths = {"user", "store"})
    List<FavoriteStore> findByStoreId(UUID storeId);

    boolean existsByUserIdAndStoreId(UUID userId, UUID storeId);

    long deleteByUserIdAndStoreId(UUID userId, UUID storeId);
}
