package com.LastBite.modules.discovery.repository;

import com.LastBite.modules.discovery.entity.DiscoveryCollection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiscoveryCollectionRepository extends JpaRepository<DiscoveryCollection, UUID> {

    boolean existsBySlug(String slug);

    Optional<DiscoveryCollection> findBySlug(String slug);

    List<DiscoveryCollection> findByActiveTrueOrderByDisplayOrderAscSlugAsc();

    Page<DiscoveryCollection> findAllByOrderByDisplayOrderAscSlugAsc(Pageable pageable);
}
