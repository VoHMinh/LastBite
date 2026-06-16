package com.LastBite.modules.merchant.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.media.entity.MediaUpload;
import com.LastBite.modules.merchant.enums.ReviewStatus;
import com.LastBite.modules.store.entity.Store;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Entity
@Table(name = "merchant_documents")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantDocument extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_profile_id")
    private MerchantBusinessProfile businessProfile;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_application_id")
    private StoreReviewApplication reviewApplication;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_upload_id", nullable = false, unique = true)
    private MediaUpload mediaUpload;
    @Column(name = "document_type", nullable = false, length = 60)
    private String documentType;
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private ReviewStatus reviewStatus;
    @Column(name = "expires_at")
    private LocalDate expiresAt;
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;
}
