package com.LastBite.modules.bag.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.bag.dto.response.PublicBagDetailResponse;
import com.LastBite.modules.bag.dto.response.PublicBagSummaryResponse;
import com.LastBite.modules.bag.enums.BagSize;
import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.bag.repository.BagDailyStockRepository;
import com.LastBite.modules.bag.repository.BagDiscoveryProjection;
import com.LastBite.modules.bag.service.BagDiscoveryServicePort;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.user.repository.FavoriteStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BagDiscoveryService implements BagDiscoveryServicePort {

    private static final int DEFAULT_LIMIT = 50;
    private static final double DEFAULT_RADIUS_KM = 5.0;

    private final BagDailyStockRepository stockRepository;
    private final FavoriteStoreRepository favoriteStoreRepository;
    private final BagPricingService pricingService;
    private final Clock clock;

    @Cacheable(value = "bag-discovery",
            key = "'today:' + #userId + ':' + #lat + ':' + #lng + ':' + #radiusKm + ':' + #dietType + ':' + #bagType + ':' + #sort + ':' + #limit")
    public List<PublicBagSummaryResponse> today(UUID userId, Double lat, Double lng, Double radiusKm, DietType dietType,
                                                BagType bagType, String sort, Integer limit) {
        return discover(userId, lat, lng, radiusKm, null, dietType, bagType, null, sort, limit);
    }

    @Cacheable(value = "bag-discovery",
            key = "'nearby:' + #userId + ':' + #lat + ':' + #lng + ':' + #radiusKm + ':' + #category + ':' + #dietType + ':' + #bagType + ':' + #district + ':' + #sort + ':' + #limit")
    public List<PublicBagSummaryResponse> discover(UUID userId, Double lat, Double lng, Double radiusKm, StoreCategory category,
                                                    DietType dietType, BagType bagType, String district,
                                                    String sort, Integer limit) {
        String normalizedSort = normalizeSort(sort);
        int normalizedLimit = normalizeLimit(limit);
        int queryLimit = normalizedSort.equals("price")
                ? Math.min(100, Math.max(normalizedLimit * 3, DEFAULT_LIMIT))
                : normalizedLimit;
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        String categoryValue = category == null ? null : category.name();
        String dietTypeValue = dietType == null ? null : dietType.name();
        String bagTypeValue = bagType == null ? null : bagType.name();
        String normalizedDistrict = normalize(district);
        boolean hasLat = lat != null;
        boolean hasLng = lng != null;
        if (hasLat != hasLng) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Cần truyền đủ cả lat và lng khi tìm theo vị trí");
        }
        if (radiusKm != null && (radiusKm <= 0 || radiusKm > 50)) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Bán kính tìm kiếm phải lớn hơn 0 và tối đa 50 km");
        }

        List<BagDiscoveryProjection> rows;
        if (hasLat) {
            rows = stockRepository.discoverWithLocation(today, now, lat, lng,
                    radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm,
                    categoryValue, dietTypeValue, bagTypeValue, normalizedDistrict, normalizedSort, queryLimit);
        } else {
            rows = stockRepository.discoverWithoutLocation(today, now,
                    categoryValue, dietTypeValue, bagTypeValue, normalizedDistrict, normalizedSort, queryLimit);
        }
        return sortSummaries(rows.stream().map(row -> toSummary(row, userId)).toList(), normalizedSort, normalizedLimit);
    }

    @Cacheable(value = "bag-detail", key = "#bagId + ':' + #userId")
    public PublicBagDetailResponse detail(UUID bagId, UUID userId) {
        BagDiscoveryProjection row = stockRepository.findPublicBagDetail(bagId, LocalDate.now(clock), LocalTime.now(clock))
                .orElseThrow(() -> new ApiException(ErrorCode.BAG_NOT_FOUND));
        return toDetail(row, userId);
    }

    @Cacheable(value = "store-bags", key = "#storeId + ':' + #limit")
    public List<PublicBagSummaryResponse> storeBags(UUID storeId, Integer limit) {
        return stockRepository.findPublicStoreBags(storeId, LocalDate.now(clock), LocalTime.now(clock), normalizeLimit(limit))
                .stream()
                .map(row -> toSummary(row, null))
                .sorted(Comparator.comparing(PublicBagSummaryResponse::isSoldOut)
                        .thenComparing(PublicBagSummaryResponse::getPickupStartTime))
                .toList();
    }

    private PublicBagSummaryResponse toSummary(BagDiscoveryProjection row, UUID userId) {
        var price = pricingService.currentPrice(
                row.getMinimumValue(),
                row.getBaseSalePrice(),
                row.getDynamicMinPrice(),
                row.getDynamicMaxPrice(),
                Boolean.TRUE.equals(row.getDynamicPricingEnabled()),
                row.getStockDate(),
                row.getPickupEndTime());

        return PublicBagSummaryResponse.builder()
                .bagId(row.getBagId())
                .storeId(row.getStoreId())
                .storeName(row.getStoreName())
                .storeSlug(row.getStoreSlug())
                .storeAddress(row.getStoreAddress())
                .storeLogoUrl(row.getStoreLogoUrl())
                .storeCoverImageUrl(row.getStoreCoverImageUrl())
                .storeAvgRating(row.getStoreAvgRating())
                .storeTotalRatings(row.getStoreTotalRatings())
                .favoriteStore(isFavoriteStore(userId, row.getStoreId()))
                .district(row.getDistrict())
                .city(row.getCity())
                .lat(row.getLat())
                .lng(row.getLng())
                .name(row.getName())
                .description(row.getDescription())
                .bagType(BagType.valueOf(row.getBagType()))
                .dietType(DietType.valueOf(row.getDietType()))
                .category(StoreCategory.valueOf(row.getCategory()))
                .bagSize(BagSize.valueOf(row.getBagSize()))
                .photos(parsePhotos(row.getPhotos()))
                .minimumValue(row.getMinimumValue())
                .baseSalePrice(row.getBaseSalePrice())
                .currentSalePrice(price.currentSalePrice())
                .savingsAmount(price.savingsAmount())
                .currentDiscountPercent(price.currentDiscountPercent())
                .dynamicMinPrice(row.getDynamicMinPrice())
                .dynamicMaxPrice(row.getDynamicMaxPrice())
                .dynamicPricingEnabled(Boolean.TRUE.equals(row.getDynamicPricingEnabled()))
                .platformFee(row.getPlatformFee())
                .maxPerOrder(valueOrZero(row.getMaxPerOrder()))
                .containerProvided(Boolean.TRUE.equals(row.getContainerProvided()))
                .carrierBagProvided(Boolean.TRUE.equals(row.getCarrierBagProvided()))
                .packagingNote(row.getPackagingNote())
                .stockDate(row.getStockDate())
                .pickupStartTime(row.getPickupStartTime())
                .pickupEndTime(row.getPickupEndTime())
                .quantity(valueOrZero(row.getQuantity()))
                .reserved(valueOrZero(row.getReserved()))
                .sold(valueOrZero(row.getSold()))
                .available(valueOrZero(row.getAvailable()))
                .soldOut(valueOrZero(row.getAvailable()) <= 0)
                .distanceKm(row.getDistanceKm() == null ? null : Math.round(row.getDistanceKm() * 100.0) / 100.0)
                .minutesUntilPickup(minutesUntilPickup(row.getPickupStartTime(), row.getPickupEndTime()))
                .pickupActive(isPickupActive(row.getPickupStartTime(), row.getPickupEndTime()))
                .build();
    }

    private PublicBagDetailResponse toDetail(BagDiscoveryProjection row, UUID userId) {
        PublicBagSummaryResponse summary = toSummary(row, userId);
        return PublicBagDetailResponse.builder()
                .bagId(summary.getBagId())
                .storeId(summary.getStoreId())
                .storeName(summary.getStoreName())
                .storeSlug(summary.getStoreSlug())
                .storeAddress(summary.getStoreAddress())
                .storeLogoUrl(summary.getStoreLogoUrl())
                .storeCoverImageUrl(summary.getStoreCoverImageUrl())
                .storeAvgRating(summary.getStoreAvgRating())
                .storeTotalRatings(summary.getStoreTotalRatings())
                .favoriteStore(summary.isFavoriteStore())
                .district(summary.getDistrict())
                .city(summary.getCity())
                .lat(summary.getLat())
                .lng(summary.getLng())
                .name(summary.getName())
                .description(summary.getDescription())
                .bagType(summary.getBagType())
                .dietType(summary.getDietType())
                .category(summary.getCategory())
                .bagSize(summary.getBagSize())
                .photos(summary.getPhotos())
                .minimumValue(summary.getMinimumValue())
                .baseSalePrice(summary.getBaseSalePrice())
                .currentSalePrice(summary.getCurrentSalePrice())
                .savingsAmount(summary.getSavingsAmount())
                .currentDiscountPercent(summary.getCurrentDiscountPercent())
                .dynamicMinPrice(summary.getDynamicMinPrice())
                .dynamicMaxPrice(summary.getDynamicMaxPrice())
                .dynamicPricingEnabled(summary.isDynamicPricingEnabled())
                .platformFee(summary.getPlatformFee())
                .maxPerOrder(summary.getMaxPerOrder())
                .containerProvided(summary.isContainerProvided())
                .carrierBagProvided(summary.isCarrierBagProvided())
                .packagingNote(summary.getPackagingNote())
                .stockDate(summary.getStockDate())
                .pickupStartTime(summary.getPickupStartTime())
                .pickupEndTime(summary.getPickupEndTime())
                .quantity(summary.getQuantity())
                .reserved(summary.getReserved())
                .sold(summary.getSold())
                .available(summary.getAvailable())
                .soldOut(summary.isSoldOut())
                .distanceKm(summary.getDistanceKm())
                .minutesUntilPickup(summary.getMinutesUntilPickup())
                .pickupActive(summary.isPickupActive())
                .build();
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) return "pickup_time";
        String value = sort.trim().toLowerCase();
        if (!List.of("pickup_time", "distance", "price").contains(value)) {
            throw new ApiException(ErrorCode.INVALID_SORT_FIELD,
                    "Sort chỉ hỗ trợ pickup_time, distance hoặc price");
        }
        return value;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) return DEFAULT_LIMIT;
        if (limit < 1 || limit > 100) {
            throw new ApiException(ErrorCode.INVALID_PAGINATION, "Limit phải từ 1 đến 100");
        }
        return limit;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<PublicBagSummaryResponse> sortSummaries(List<PublicBagSummaryResponse> values, String sort, int limit) {
        Comparator<PublicBagSummaryResponse> comparator = Comparator.comparing(PublicBagSummaryResponse::isSoldOut);
        comparator = switch (sort) {
            case "distance" -> comparator.thenComparing(
                    PublicBagSummaryResponse::getDistanceKm,
                    Comparator.nullsLast(Double::compareTo));
            case "price" -> comparator.thenComparing(PublicBagSummaryResponse::getCurrentSalePrice);
            default -> comparator.thenComparing(PublicBagSummaryResponse::getPickupStartTime);
        };
        return values.stream()
                .sorted(comparator.thenComparing(PublicBagSummaryResponse::getPickupStartTime))
                .limit(limit)
                .toList();
    }

    private List<String> parsePhotos(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .filter(photo -> !photo.isBlank())
                .map(String::trim)
                .toList();
    }

    private long minutesUntilPickup(LocalTime start, LocalTime end) {
        LocalTime now = LocalTime.now(clock);
        if (!now.isBefore(start) && now.isBefore(end)) return 0;
        if (now.isAfter(start)) return 0;
        return Math.max(0, Duration.between(now, start).toMinutes());
    }

    private boolean isPickupActive(LocalTime start, LocalTime end) {
        LocalTime now = LocalTime.now(clock);
        return !now.isBefore(start) && now.isBefore(end);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isFavoriteStore(UUID userId, UUID storeId) {
        return userId != null && favoriteStoreRepository.existsByUserIdAndStoreId(userId, storeId);
    }
}
