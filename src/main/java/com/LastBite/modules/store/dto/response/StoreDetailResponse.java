package com.LastBite.modules.store.dto.response;

import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.store.enums.StoreStatus;
import com.LastBite.modules.store.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Chi tiết đầy đủ của cửa hàng — gồm lịch mở cửa và thông tin giấy phép kinh doanh.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreDetailResponse implements Serializable {
    private UUID id;
    private UUID ownerId;
    private UUID businessProfileId;
    private String name;
    private String slug;
    private String description;
    private StoreCategory category;
    private String phone;
    private String email;
    private String address;
    private String district;
    private String city;
    private Double lat;
    private Double lng;
    private String pickupInstructions;
    private String storefrontImageUrl;
    private String menuImageUrl;
    private String coverImageUrl;
    private String logoUrl;
    private List<String> galleryImageUrls;
    private String businessLicenseNumber;
    private String businessLicenseImageUrl;
    private StoreStatus status;
    private VerificationStatus verificationStatus;
    private String rejectionReason;
    private double avgRating;
    private int totalRatings;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ScheduleResponse> schedules;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleResponse implements Serializable {
        private int dayOfWeek;
        private LocalTime openTime;
        private LocalTime closeTime;
        private boolean isOpen;
    }
}
