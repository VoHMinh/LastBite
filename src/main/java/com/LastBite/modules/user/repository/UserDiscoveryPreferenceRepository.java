package com.LastBite.modules.user.repository;

import com.LastBite.modules.user.entity.UserDiscoveryPreference;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserDiscoveryPreferenceRepository extends JpaRepository<UserDiscoveryPreference, UUID> {

    @EntityGraph(attributePaths = {"preferredCollectionTimes"})
    Optional<UserDiscoveryPreference> findByUserId(UUID userId);

    long deleteByUserId(UUID userId);
}
