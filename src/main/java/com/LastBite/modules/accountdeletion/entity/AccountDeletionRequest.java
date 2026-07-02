package com.LastBite.modules.accountdeletion.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.accountdeletion.enums.AccountDeletionRequesterType;
import com.LastBite.modules.accountdeletion.enums.AccountDeletionRequestStatus;
import com.LastBite.modules.accountdeletion.enums.AccountDeletionSource;
import com.LastBite.modules.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "account_deletion_requests", indexes = {
        @Index(name = "idx_account_deletion_user_status", columnList = "user_id,status"),
        @Index(name = "idx_account_deletion_status", columnList = "status"),
        @Index(name = "idx_account_deletion_token_hash", columnList = "token_hash"),
        @Index(name = "idx_account_deletion_scheduled", columnList = "scheduled_deletion_at")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeletionRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "request_email", length = 255)
    private String requestEmail;

    @Column(name = "request_phone", length = 30)
    private String requestPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "requester_type", nullable = false, length = 30)
    private AccountDeletionRequesterType requesterType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountDeletionSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AccountDeletionRequestStatus status;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "blocker_summary", columnDefinition = "TEXT")
    private String blockerSummary;

    @Column(name = "token_hash", unique = true, length = 255)
    private String tokenHash;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "scheduled_deletion_at")
    private Instant scheduledDeletionAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;
}
