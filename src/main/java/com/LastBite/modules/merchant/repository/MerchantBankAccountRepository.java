package com.LastBite.modules.merchant.repository;

import com.LastBite.modules.merchant.entity.MerchantBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MerchantBankAccountRepository extends JpaRepository<MerchantBankAccount, UUID> {
    List<MerchantBankAccount> findAllByBusinessProfileOwnerIdOrderByCreatedAtAsc(UUID ownerId);
    boolean existsByBusinessProfileId(UUID businessProfileId);
}
