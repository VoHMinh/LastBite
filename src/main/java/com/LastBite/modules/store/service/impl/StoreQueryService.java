package com.LastBite.modules.store.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.store.dto.response.PublicStoreDetailResponse;
import com.LastBite.modules.store.dto.response.StoreResponse;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.store.enums.VerificationStatus;
import com.LastBite.modules.store.repository.StoreRepository;
import com.LastBite.modules.store.service.StoreQueryServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Truy vấn cửa hàng công khai cho khách hàng — chỉ đọc, có cache.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreQueryService implements StoreQueryServicePort {

    private final StoreRepository storeRepository;

    /**
     * Tìm cửa hàng đã xác minh với bộ lọc tùy chọn (cache 5 phút).
     */
    @Cacheable(value = "store-list",
            key = "'search:' + #keyword + ':' + #category + ':' + #city + ':' + #district + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<StoreResponse> searchStores(String keyword, StoreCategory category, String city, String district,
                                            Pageable pageable) {
        return storeRepository
                .searchStores(VerificationStatus.VERIFIED, normalize(keyword), category,
                        normalize(city), normalize(district), pageable)
                .map(this::toResponse);
    }

    /**
     * Lấy chi tiết cửa hàng theo slug (cache 15 phút).
     */
    @Cacheable(value = "store-by-slug", key = "#slug")
    public PublicStoreDetailResponse getStoreBySlug(String slug) {
        Store store = storeRepository.findBySlug(slug)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));

        // Chỉ hiển thị cửa hàng đã xác minh cho public
        if (store.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ApiException(ErrorCode.STORE_NOT_FOUND, "Cửa hàng chưa được xác minh");
        }

        return toPublicDetailResponse(store);
    }

    private PublicStoreDetailResponse toPublicDetailResponse(Store store) {
        var schedules = store.getSchedules().stream()
                .map(s -> PublicStoreDetailResponse.ScheduleResponse.builder()
                        .dayOfWeek(s.getDayOfWeek())
                        .openTime(s.getOpenTime())
                        .closeTime(s.getCloseTime())
                        .isOpen(s.isOpen())
                        .build())
                .toList();

        return PublicStoreDetailResponse.builder()
                .id(store.getId())
                .name(store.getName())
                .slug(store.getSlug())
                .description(store.getDescription())
                .category(store.getCategory())
                .phone(store.getPhone())
                .email(store.getEmail())
                .address(store.getAddress())
                .district(store.getDistrict())
                .city(store.getCity())
                .lat(store.getLat())
                .lng(store.getLng())
                .coverImageUrl(store.getCoverImageUrl())
                .logoUrl(store.getLogoUrl())
                .status(store.getStatus())
                .avgRating(store.getAvgRating())
                .totalRatings(store.getTotalRatings())
                .createdAt(store.getCreatedAt())
                .schedules(schedules)
                .build();
    }

    private StoreResponse toResponse(Store store) {
        return StoreResponse.builder()
                .id(store.getId())
                .name(store.getName())
                .slug(store.getSlug())
                .description(store.getDescription())
                .category(store.getCategory())
                .address(store.getAddress())
                .district(store.getDistrict())
                .city(store.getCity())
                .lat(store.getLat())
                .lng(store.getLng())
                .coverImageUrl(store.getCoverImageUrl())
                .logoUrl(store.getLogoUrl())
                .status(store.getStatus())
                .verificationStatus(store.getVerificationStatus())
                .avgRating(store.getAvgRating())
                .totalRatings(store.getTotalRatings())
                .createdAt(store.getCreatedAt())
                .build();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
