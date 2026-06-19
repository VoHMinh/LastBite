package com.LastBite.modules.auth.dto.response;

import com.LastBite.modules.auth.enums.AuthProvider;
import com.LastBite.modules.auth.enums.AccountType;
import com.LastBite.modules.auth.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse implements Serializable {

    private UUID id;
    private String email;
    private String username;
    private String fullName;
    private String phone;
    private String avatarUrl;
    private AccountType accountType;
    private List<String> roles;
    private UserStatus status;
    private AuthProvider authProvider;
    private boolean emailVerified;
    private boolean phoneVerified;
    private boolean mustChangePassword;
    private Instant createdAt;
}
