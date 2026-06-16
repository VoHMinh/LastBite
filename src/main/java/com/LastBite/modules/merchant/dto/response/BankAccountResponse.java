package com.LastBite.modules.merchant.dto.response;

import com.LastBite.modules.merchant.enums.ReviewStatus;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountResponse {
    private UUID id;
    private UUID storeId;
    private String bankCode;
    private String bankName;
    private String accountHolderName;
    private String maskedAccountNumber;
    private boolean defaultAccount;
    private ReviewStatus verificationStatus;
    private String rejectionReason;
}
