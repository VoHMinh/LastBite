package com.LastBite.modules.auth.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.auth.enums.AccountType;
import com.LastBite.modules.auth.enums.AuthProvider;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Column(unique = true, length = 255)
    private String email;

    @Column(unique = true, length = 80)
    private String username;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(length = 20, unique = true)
    private String phone;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    @Builder.Default
    private AccountType accountType = AccountType.PLATFORM;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "phone_verified", nullable = false)
    @Builder.Default
    private boolean phoneVerified = false;

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    @Column(name = "deletion_scheduled_at")
    private Instant deletionScheduledAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    public boolean hasRole(UserRole role) {
        return roles.stream().anyMatch(item -> item.getCode() == role);
    }

    public void addRole(Role role) {
        roles.add(role);
    }
}
