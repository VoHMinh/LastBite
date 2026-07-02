package com.LastBite.modules.accountdeletion.repository;

import com.LastBite.modules.accountdeletion.entity.AccountDeletionRequest;
import com.LastBite.modules.accountdeletion.enums.AccountDeletionRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest, UUID> {

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Optional<AccountDeletionRequest> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
            UUID userId,
            Collection<AccountDeletionRequestStatus> statuses);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Optional<AccountDeletionRequest> findByTokenHashAndStatusAndTokenExpiresAtAfter(
            String tokenHash,
            AccountDeletionRequestStatus status,
            Instant now);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Optional<AccountDeletionRequest> findByTokenHashAndStatusInAndTokenExpiresAtAfter(
            String tokenHash,
            Collection<AccountDeletionRequestStatus> statuses,
            Instant now);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Page<AccountDeletionRequest> findAllByStatus(AccountDeletionRequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Page<AccountDeletionRequest> findAll(Pageable pageable);
}
