package com.LastBite.modules.discovery.repository;

import com.LastBite.modules.discovery.entity.DiscoveryCollectionItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiscoveryCollectionItemRepository extends JpaRepository<DiscoveryCollectionItem, UUID> {

    @EntityGraph(attributePaths = {"bag"})
    List<DiscoveryCollectionItem> findByCollectionIdOrderByPinnedOrderAscAddedAtAsc(UUID collectionId);

    Optional<DiscoveryCollectionItem> findByCollectionIdAndBagId(UUID collectionId, UUID bagId);

    boolean existsByCollectionIdAndBagId(UUID collectionId, UUID bagId);

    long deleteByCollectionIdAndBagId(UUID collectionId, UUID bagId);
}
