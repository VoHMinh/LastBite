package com.LastBite.modules.merchant.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.merchant.enums.ReviewStatus;
import com.LastBite.modules.store.entity.Store;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "merchant_bank_accounts")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantBankAccount extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_profile_id", nullable = false)
    private MerchantBusinessProfile businessProfile;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;
    @Column(name = "bank_code", nullable = false, length = 30)
    private String bankCode;
    @Column(name = "bank_name", nullable = false, length = 150)
    private String bankName;
    @Column(name = "account_holder_name", nullable = false, length = 255)
    private String accountHolderName;
    @Column(name = "account_number_encrypted", nullable = false, columnDefinition = "TEXT")
    private String accountNumberEncrypted;
    @Column(name = "account_number_last4", nullable = false, length = 4)
    private String accountNumberLast4;
    @Column(name = "is_default", nullable = false)
    private boolean defaultAccount;
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    private ReviewStatus verificationStatus;
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;
}
