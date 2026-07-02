package com.LastBite.modules.accountdeletion.dto.request;

import com.LastBite.modules.accountdeletion.enums.AccountDeletionRequesterType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicAccountDeletionRequest {
    @NotBlank
    @Email
    private String email;

    @Size(max = 30)
    private String phone;

    @NotNull
    private AccountDeletionRequesterType requesterType;

    @Size(max = 1000)
    private String reason;
}
