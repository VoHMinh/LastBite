package com.LastBite.modules.refund.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefundDestinationRequest {
    @NotBlank
    @Size(max = 30)
    private String bankCode;

    @NotBlank
    @Size(max = 150)
    private String bankName;

    @NotBlank
    @Size(max = 255)
    private String accountHolderName;

    @NotBlank
    @Pattern(regexp = "^[0-9A-Za-z .-]{4,40}$")
    private String accountNumber;
}
