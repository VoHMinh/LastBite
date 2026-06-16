package com.LastBite.modules.merchant.dto.response;

import com.LastBite.modules.merchant.enums.BusinessLegalType;
import com.LastBite.modules.merchant.enums.ReviewStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessProfileResponse {
    private UUID id;
    private UUID ownerUserId;
    private BusinessLegalType legalType;
    private String legalName;
    private String representativeFullName;
    private String representativePhone;
    private String representativeEmail;
    private String identityDocumentType;
    private String maskedIdentityDocumentNumber;
    private String taxCode;
    private String registrationNumber;
    private String parentCompanyName;
    private String parentCompanyTaxCode;
    private String businessAddress;
    private ReviewStatus reviewStatus;
    private String rejectionReason;
    private Instant approvedAt;
}
