package com.LastBite.modules.store.dto.response;

import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.store.enums.StoreStatus;
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
 * Chi tiết cửa hàng công khai cho khách hàng.
 * Không lộ các field chỉ dành cho chủ cửa hàng/admin như giấy phép kinh doanh.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicStoreDetailResponse implements Serializable {
    private UUID id;
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
    private String coverImageUrl;
    private String logoUrl;
    private List<String> galleryImageUrls;
    private StoreStatus status;
    private double avgRating;
    private int totalRatings;
    private Instant createdAt;
    private List<ScheduleResponse> schedules;

    // Reliability / trust score fields (from StoreReliabilityStats)
    private Integer storeTotalBagsListed;
    private Integer storeTotalBagsFulfilled;
    private Double storeFulfillmentRate;
    private Integer storeWarningCount;

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
