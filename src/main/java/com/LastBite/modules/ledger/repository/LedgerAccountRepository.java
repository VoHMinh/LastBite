package com.LastBite.modules.ledger.repository;

import com.LastBite.modules.ledger.entity.LedgerAccount;
import com.LastBite.modules.ledger.enums.LedgerAccountType;
import com.LastBite.modules.ledger.enums.LedgerOwnerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
    Optional<LedgerAccount> findByOwnerTypeAndOwnerIdAndAccountTypeAndCurrency(
            LedgerOwnerType ownerType, UUID ownerId, LedgerAccountType accountType, String currency);
}
