package com.LastBite.modules.merchant.dto.response;

import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.merchant.enums.StoreMemberStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StoreMemberResponse {
    private UUID id;
    private UUID userId;
    private UUID storeId;
    private String username;
    private String fullName;
    private String phone;
    private UserRole role;
    private StoreMemberStatus status;
    private boolean mustChangePassword;
    private String temporaryPassword;
    private Instant joinedAt;
}
