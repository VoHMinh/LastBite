package com.LastBite.modules.merchant.repository;

import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.merchant.entity.MerchantStoreMember;
import com.LastBite.modules.merchant.enums.StoreMemberStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantStoreMemberRepository extends JpaRepository<MerchantStoreMember, UUID> {
    @EntityGraph(attributePaths = {"user", "store", "role"})
    Optional<MerchantStoreMember> findByUserId(UUID userId);

    @EntityGraph(attributePaths = {"user", "store", "role"})
    Optional<MerchantStoreMember> findByUserIdAndStoreIdAndStatus(UUID userId, UUID storeId, StoreMemberStatus status);

    @EntityGraph(attributePaths = {"user", "role"})
    List<MerchantStoreMember> findAllByStoreIdOrderByCreatedAtAsc(UUID storeId);

    boolean existsByUserIdAndStoreIdAndRoleCodeAndStatus(
            UUID userId, UUID storeId, UserRole role, StoreMemberStatus status);
}
