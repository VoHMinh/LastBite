package com.LastBite.modules.merchant.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.auth.entity.Role;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.merchant.enums.StoreMemberStatus;
import com.LastBite.modules.store.entity.Store;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "merchant_store_members",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_store_member_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uq_store_member_pair", columnNames = {"store_id", "user_id"})
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantStoreMember extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private StoreMemberStatus status = StoreMemberStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Column(name = "joined_at", nullable = false)
    @Builder.Default
    private Instant joinedAt = Instant.now();
}
