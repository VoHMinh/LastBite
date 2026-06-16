package com.LastBite.modules.merchant.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.merchant.enums.ReviewStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "merchant_business_profile_versions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"business_profile_id", "version_number"}))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantBusinessProfileVersion extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_profile_id", nullable = false)
    private MerchantBusinessProfile businessProfile;
    @Column(name = "version_number", nullable = false)
    private int versionNumber;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private ReviewStatus reviewStatus;
    @Column(name = "submitted_at")
    private Instant submittedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;
}
