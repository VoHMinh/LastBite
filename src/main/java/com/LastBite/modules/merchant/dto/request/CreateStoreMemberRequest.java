package com.LastBite.modules.merchant.dto.request;

import com.LastBite.modules.auth.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateStoreMemberRequest {
    @NotBlank
    @Size(min = 4, max = 80)
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$")
    private String username;
    @NotBlank
    @Size(max = 255)
    private String fullName;
    @Size(max = 20)
    private String phone;
    @NotNull
    private UserRole role;
}
