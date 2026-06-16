package com.LastBite.modules.merchant.dto.request;

import com.LastBite.modules.merchant.enums.BusinessLegalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BusinessProfileRequest {
    @NotNull
    private BusinessLegalType legalType;
    @Size(max = 255)
    private String legalName;
    @NotBlank
    @Size(max = 255)
    private String representativeFullName;
    @Size(max = 20)
    private String representativePhone;
    @Size(max = 255)
    private String representativeEmail;
    @Size(max = 30)
    private String identityDocumentType;
    @Size(max = 100)
    private String identityDocumentNumber;
    @Size(max = 50)
    private String taxCode;
    @Size(max = 100)
    private String registrationNumber;
    @Size(max = 255)
    private String parentCompanyName;
    @Size(max = 50)
    private String parentCompanyTaxCode;
    private String businessAddress;
}
