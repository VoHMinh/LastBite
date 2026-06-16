package com.LastBite.modules.merchant.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.merchant.enums.BusinessLegalType;
import com.LastBite.modules.merchant.enums.ReviewStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "merchant_business_profiles")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantBusinessProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false, unique = true)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "legal_type", nullable = false, length = 40)
    private BusinessLegalType legalType;

    @Column(name = "legal_name", length = 255)
    private String legalName;

    @Column(name = "representative_full_name", nullable = false, length = 255)
    private String representativeFullName;

    @Column(name = "representative_phone", length = 20)
    private String representativePhone;

    @Column(name = "representative_email", length = 255)
    private String representativeEmail;

    @Column(name = "identity_document_type", length = 30)
    private String identityDocumentType;

    @Column(name = "identity_document_number", length = 100)
    private String identityDocumentNumber;

    @Column(name = "tax_code", length = 50)
    private String taxCode;

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(name = "parent_company_name", length = 255)
    private String parentCompanyName;

    @Column(name = "parent_company_tax_code", length = 50)
    private String parentCompanyTaxCode;

    @Column(name = "business_address", columnDefinition = "TEXT")
    private String businessAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    @Builder.Default
    private ReviewStatus reviewStatus = ReviewStatus.DRAFT;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "approved_at")
    private Instant approvedAt;
}
