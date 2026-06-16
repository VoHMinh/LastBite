package com.LastBite.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StoreLoginRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
}
