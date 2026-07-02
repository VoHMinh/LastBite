package com.LastBite.modules.accountdeletion.dto.response;

import com.LastBite.modules.accountdeletion.enums.AccountDeletionRequesterType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AccountDeletionEligibilityResponse {
    private boolean eligible;
    private AccountDeletionRequesterType requesterType;
    private List<String> blockers;
    private AccountDeletionResponse pendingRequest;
}
