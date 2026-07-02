package com.LastBite.modules.accountdeletion.dto.response;

import com.LastBite.modules.accountdeletion.enums.AccountDeletionRequesterType;
import com.LastBite.modules.accountdeletion.enums.AccountDeletionRequestStatus;
import com.LastBite.modules.accountdeletion.enums.AccountDeletionSource;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AccountDeletionResponse {
    private UUID id;
    private UUID userId;
    private String email;
    private AccountDeletionRequesterType requesterType;
    private AccountDeletionSource source;
    private AccountDeletionRequestStatus status;
    private String reason;
    private String blockerSummary;
    private Instant requestedAt;
    private Instant verifiedAt;
    private Instant scheduledDeletionAt;
    private Instant finalizedAt;
    private String adminNote;
}
