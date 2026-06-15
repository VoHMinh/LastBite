package com.LastBite.modules.merchant.repository;

import com.LastBite.modules.merchant.entity.MerchantBusinessProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantBusinessProfileRepository extends JpaRepository<MerchantBusinessProfile, UUID> {
    Optional<MerchantBusinessProfile> findByOwnerId(UUID ownerId);
    boolean existsByOwnerId(UUID ownerId);
}
