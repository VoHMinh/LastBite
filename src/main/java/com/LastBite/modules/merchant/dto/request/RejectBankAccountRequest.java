package com.LastBite.modules.merchant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectBankAccountRequest {
    @NotBlank
    @Size(max = 1000)
    private String rejectionReason;
}
