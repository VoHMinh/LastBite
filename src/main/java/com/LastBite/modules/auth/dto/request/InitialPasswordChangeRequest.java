package com.LastBite.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InitialPasswordChangeRequest {
    @NotBlank
    private String currentPassword;
    @NotBlank
    @Size(min = 8, max = 100)
    private String newPassword;
}
