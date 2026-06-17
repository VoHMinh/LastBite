package com.LastBite.modules.merchant.repository;

import com.LastBite.modules.merchant.entity.MerchantBankAccount;
import com.LastBite.modules.merchant.enums.BankAccountVerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantBankAccountRepository extends JpaRepository<MerchantBankAccount, UUID> {
    List<MerchantBankAccount> findAllByBusinessProfileOwnerIdOrderByCreatedAtAsc(UUID ownerId);
    boolean existsByBusinessProfileId(UUID businessProfileId);
    Optional<MerchantBankAccount> findFirstByBusinessProfileIdAndVerificationStatusOrderByDefaultAccountDescCreatedAtAsc(
            UUID businessProfileId,
            BankAccountVerificationStatus verificationStatus);

    @EntityGraph(attributePaths = {"businessProfile", "businessProfile.owner", "store"})
    @Query("""
        SELECT a FROM MerchantBankAccount a
        WHERE (:status IS NULL OR a.verificationStatus = :status)
          AND (:businessProfileId IS NULL OR a.businessProfile.id = :businessProfileId)
          AND (:storeId IS NULL OR a.store.id = :storeId)
    """)
    Page<MerchantBankAccount> searchAdmin(@Param("status") BankAccountVerificationStatus status,
                                          @Param("businessProfileId") UUID businessProfileId,
                                          @Param("storeId") UUID storeId,
                                          Pageable pageable);
}
