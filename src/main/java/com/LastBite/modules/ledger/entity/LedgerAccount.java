package com.LastBite.modules.ledger.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.ledger.enums.LedgerAccountType;
import com.LastBite.modules.ledger.enums.LedgerOwnerType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ledger_accounts",
        uniqueConstraints = @UniqueConstraint(name = "uq_ledger_accounts_owner_type",
                columnNames = {"owner_type", "owner_id", "account_type", "currency"}))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerAccount extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 30)
    private LedgerOwnerType ownerType;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 40)
    private LedgerAccountType accountType;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "VND";

    @Column(nullable = false, precision = 14, scale = 0)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;
}
