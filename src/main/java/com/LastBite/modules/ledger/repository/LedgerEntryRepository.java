package com.LastBite.modules.ledger.repository;

import com.LastBite.modules.ledger.entity.LedgerEntry;
import com.LastBite.modules.ledger.enums.LedgerAccountType;
import com.LastBite.modules.ledger.enums.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    boolean existsByOrderIdAndEntryType(UUID orderId, LedgerEntryType entryType);

    List<LedgerEntry> findAllByOrderIdAndEntryType(UUID orderId, LedgerEntryType entryType);

    @Query("""
        SELECT e FROM LedgerEntry e
        JOIN FETCH e.account a
        LEFT JOIN FETCH e.order o
        LEFT JOIN FETCH o.store st
        LEFT JOIN FETCH st.businessProfile bp
        WHERE a.accountType = :accountType
          AND e.settlementId IS NULL
          AND e.availableAt IS NOT NULL
          AND e.availableAt <= :now
        ORDER BY st.id, e.createdAt
    """)
    List<LedgerEntry> findUnsettledAvailable(@Param("accountType") LedgerAccountType accountType,
                                             @Param("now") Instant now);
}
