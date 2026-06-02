package com.LastBite.modules.store.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.store.enums.StoreStatus;
import com.LastBite.modules.store.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Cửa hàng do STORE_OWNER sở hữu.
 * <p>
 * Lịch mở cửa được fetch bằng {@code @NamedEntityGraph} để tránh N+1.
 */
@Entity
@Table(name = "stores", indexes = {
        @Index(name = "idx_stores_slug", columnList = "slug"),
        @Index(name = "idx_stores_owner_id", columnList = "owner_id"),
        @Index(name = "idx_stores_status", columnList = "status"),
        @Index(name = "idx_stores_category", columnList = "category"),
        @Index(name = "idx_stores_verification", columnList = "verification_status")
})
@NamedEntityGraph(name = "Store.withSchedules",
        attributeNodes = @NamedAttributeNode("schedules"))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Store extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false, unique = true)
    private User owner;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 300)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StoreCategory category;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String address;

    @Column(length = 100)
    private String district;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String city = "ho-chi-minh";

    private Double lat;
    private Double lng;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "cover_image_key", length = 500)
    private String coverImageKey;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "logo_key", length = 500)
    private String logoKey;

    @Column(name = "business_license_number", length = 100)
    private String businessLicenseNumber;

    @Column(name = "business_license_image_url", length = 500)
    private String businessLicenseImageUrl;

    @Column(name = "business_license_image_key", length = 500)
    private String businessLicenseImageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private StoreStatus status = StoreStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "avg_rating", nullable = false)
    @Builder.Default
    private double avgRating = 0;

    @Column(name = "total_ratings", nullable = false)
    @Builder.Default
    private int totalRatings = 0;

    // ── Schedules (prevent N+1 via @NamedEntityGraph) ──
    @OneToMany(mappedBy = "store", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StoreSchedule> schedules = new ArrayList<>();

    /** Hàm hỗ trợ thay toàn bộ lịch mở cửa cùng lúc. */
    public void replaceSchedules(List<StoreSchedule> newSchedules) {
        this.schedules.clear();
        newSchedules.forEach(s -> s.setStore(this));
        this.schedules.addAll(newSchedules);
    }
}
