package com.LastBite.modules.merchant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class BankAccountRequest {
    @NotBlank @Size(max = 30)
    private String bankCode;
    @NotBlank @Size(max = 150)
    private String bankName;
    @NotBlank @Size(max = 255)
    private String accountHolderName;
    @NotBlank @Size(min = 6, max = 30)
    private String accountNumber;
    private UUID storeId;
    private boolean defaultAccount;
}
