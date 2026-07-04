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
import java.util.List;
import java.util.UUID;

/**
 * Tóm tắt cửa hàng — dùng trong kết quả tìm kiếm (không gồm lịch mở cửa).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreResponse implements Serializable {
    private UUID id;
    private String name;
    private String slug;
    private String description;
    private StoreCategory category;
    private String address;
    private String district;
    private String city;
    private Double lat;
    private Double lng;
    private Double distanceKm;
    private String coverImageUrl;
    private String logoUrl;
    private List<String> galleryImageUrls;
    private StoreStatus status;
    private VerificationStatus verificationStatus;
    private double avgRating;
    private int totalRatings;
    private Instant createdAt;
}
