package com.LastBite.modules.store.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.util.SlugUtil;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.store.dto.request.CreateStoreRequest;
import com.LastBite.modules.store.dto.request.ScheduleRequest;
import com.LastBite.modules.store.dto.request.UpdateStoreRequest;
import com.LastBite.modules.store.dto.response.StoreDetailResponse;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.entity.StoreReliabilityStats;
import com.LastBite.modules.store.entity.StoreSchedule;
import com.LastBite.modules.store.enums.StoreStatus;
import com.LastBite.modules.store.enums.VerificationStatus;
import com.LastBite.modules.store.repository.StoreReliabilityStatsRepository;
import com.LastBite.modules.store.repository.StoreRepository;
import com.LastBite.modules.store.service.StoreServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Quản lý cửa hàng cho role STORE_OWNER.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreService implements StoreServicePort {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final StoreReliabilityStatsRepository reliabilityStatsRepository;

    /**
     * Hàm nội bộ — được AuthService gọi khi đăng ký đối tác.
     * Tạo cửa hàng cho user STORE_OWNER vừa đăng ký.
     * Đồng thời khởi tạo {@link StoreReliabilityStats} (1:1).
     */
    @Transactional
    public Store createStoreInternal(User owner, CreateStoreRequest request) {
        // Tạo slug duy nhất từ tên cửa hàng
        String slug = SlugUtil.toUniqueSlug(request.getName(), storeRepository::existsBySlug);

        Store store = Store.builder()
                .owner(owner)
                .name(request.getName().trim())
                .slug(slug)
                .description(request.getDescription())
                .category(request.getCategory())
                .phone(request.getPhone())
                .email(request.getEmail())
                .address(request.getAddress().trim())
                .district(request.getDistrict())
                .city(request.getCity() != null ? request.getCity() : "ho-chi-minh")
                .lat(request.getLat())
                .lng(request.getLng())
                .coverImageUrl(request.getCoverImageUrl())
                .logoUrl(request.getLogoUrl())
                .businessLicenseNumber(request.getBusinessLicenseNumber())
                .businessLicenseImageUrl(request.getBusinessLicenseImageUrl())
                .status(StoreStatus.ACTIVE)
                .verificationStatus(VerificationStatus.PENDING)
                .build();

        store = storeRepository.save(store);

        // Khởi tạo thống kê độ tin cậy
        StoreReliabilityStats stats = StoreReliabilityStats.builder()
                .store(store)
                .build();
        reliabilityStatsRepository.save(stats);

        log.info("Đã tạo cửa hàng: {} (slug={}) bởi người dùng {}", store.getName(), store.getSlug(), owner.getId());
        return store;
    }

    /**
     * Lấy cửa hàng của chính chủ cửa hàng (kèm lịch mở cửa).
     */
    public StoreDetailResponse getMyStore(UUID ownerId) {
        Store store = storeRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND, "Bạn chưa có cửa hàng"));
        return toDetailResponse(store);
    }

    /**
     * Cập nhật thông tin cửa hàng — xóa cache liên quan.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "store-detail", key = "#ownerId"),
            @CacheEvict(value = "store-by-slug", allEntries = true),
            @CacheEvict(value = "store-list", allEntries = true)
    })
    public StoreDetailResponse updateStore(UUID ownerId, UpdateStoreRequest request) {
        Store store = storeRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));

        if (request.getName() != null && !request.getName().isBlank()) {
            store.setName(request.getName().trim());
            // Tạo lại slug nếu đổi tên — tách currentSlug để tránh lỗi lambda capture
            String currentSlug = store.getSlug();
            store.setSlug(SlugUtil.toUniqueSlug(request.getName(), slug ->
                    !slug.equals(currentSlug) && storeRepository.existsBySlug(slug)));
        }
        if (request.getDescription() != null) store.setDescription(request.getDescription());
        if (request.getPhone() != null) store.setPhone(request.getPhone());
        if (request.getEmail() != null) store.setEmail(request.getEmail());
        if (request.getAddress() != null) store.setAddress(request.getAddress().trim());
        if (request.getDistrict() != null) store.setDistrict(request.getDistrict());
        if (request.getCity() != null) store.setCity(request.getCity());
        if (request.getLat() != null) store.setLat(request.getLat());
        if (request.getLng() != null) store.setLng(request.getLng());
        if (request.getCoverImageUrl() != null) store.setCoverImageUrl(request.getCoverImageUrl());
        if (request.getLogoUrl() != null) store.setLogoUrl(request.getLogoUrl());

        store = storeRepository.save(store);
        log.info("Đã cập nhật cửa hàng: {} (slug={})", store.getName(), store.getSlug());
        return toDetailResponse(store);
    }

    /**
     * Thay toàn bộ lịch mở cửa của cửa hàng.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "store-detail", key = "#ownerId"),
            @CacheEvict(value = "store-by-slug", allEntries = true)
    })
    public StoreDetailResponse updateSchedules(UUID ownerId, List<ScheduleRequest> requests) {
        Store store = storeRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));

        List<StoreSchedule> newSchedules = new java.util.ArrayList<>();
        for (ScheduleRequest r : requests) {
            StoreSchedule schedule = new StoreSchedule();
            schedule.setDayOfWeek(r.getDayOfWeek());
            schedule.setOpenTime(r.getOpenTime());
            schedule.setCloseTime(r.getCloseTime());
            schedule.setOpen(Boolean.TRUE.equals(r.getIsOpen()));
            newSchedules.add(schedule);
        }

        store.replaceSchedules(newSchedules);
        store = storeRepository.save(store);
        log.info("Đã cập nhật lịch mở cửa cho cửa hàng: {}", store.getSlug());
        return toDetailResponse(store);
    }

    /**
     * Tạm ngưng cửa hàng.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "store-detail", key = "#ownerId"),
            @CacheEvict(value = "store-by-slug", allEntries = true),
            @CacheEvict(value = "store-list", allEntries = true)
    })
    public StoreDetailResponse pauseStore(UUID ownerId) {
        Store store = storeRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));
        store.setStatus(StoreStatus.PAUSED);
        store = storeRepository.save(store);
        log.info("Cửa hàng đã tạm ngưng: {}", store.getSlug());
        return toDetailResponse(store);
    }

    /**
     * Kích hoạt lại cửa hàng sau khi tạm ngưng.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "store-detail", key = "#ownerId"),
            @CacheEvict(value = "store-by-slug", allEntries = true),
            @CacheEvict(value = "store-list", allEntries = true)
    })
    public StoreDetailResponse activateStore(UUID ownerId) {
        Store store = storeRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));
        store.setStatus(StoreStatus.ACTIVE);
        store = storeRepository.save(store);
        log.info("Cửa hàng đã hoạt động lại: {}", store.getSlug());
        return toDetailResponse(store);
    }

    // ── Mapper ──

    static StoreDetailResponse toDetailResponse(Store store) {
        List<StoreDetailResponse.ScheduleResponse> schedules = store.getSchedules().stream()
                .map(s -> StoreDetailResponse.ScheduleResponse.builder()
                        .dayOfWeek(s.getDayOfWeek())
                        .openTime(s.getOpenTime())
                        .closeTime(s.getCloseTime())
                        .isOpen(s.isOpen())
                        .build())
                .toList();

        return StoreDetailResponse.builder()
                .id(store.getId())
                .ownerId(store.getOwner().getId())
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
                .businessLicenseNumber(store.getBusinessLicenseNumber())
                .businessLicenseImageUrl(store.getBusinessLicenseImageUrl())
                .status(store.getStatus())
                .verificationStatus(store.getVerificationStatus())
                .rejectionReason(store.getRejectionReason())
                .avgRating(store.getAvgRating())
                .totalRatings(store.getTotalRatings())
                .createdAt(store.getCreatedAt())
                .updatedAt(store.getUpdatedAt())
                .schedules(schedules)
                .build();
    }
}
