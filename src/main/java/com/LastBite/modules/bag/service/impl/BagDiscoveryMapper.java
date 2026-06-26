package com.LastBite.modules.bag.service.impl;

import com.LastBite.modules.bag.dto.response.PublicBagDetailResponse;
import com.LastBite.modules.bag.dto.response.PublicBagSummaryResponse;
import com.LastBite.modules.bag.enums.BagSize;
import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.bag.repository.BagDiscoveryProjection;
import com.LastBite.modules.media.service.MediaUrlService;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.user.repository.FavoriteStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BagDiscoveryMapper {

    private final FavoriteStoreRepository favoriteStoreRepository;
    private final BagPricingService pricingService;
    private final MediaUrlService mediaUrlService;
    private final Clock clock;

    public PublicBagSummaryResponse toSummary(BagDiscoveryProjection row, UUID userId) {
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
                .storeLogoUrl(mediaUrlService.resolveUrl(row.getStoreLogoUrl()))
                .storeCoverImageUrl(mediaUrlService.resolveUrl(row.getStoreCoverImageUrl()))
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

    public PublicBagDetailResponse toDetail(BagDiscoveryProjection row, UUID userId) {
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

    private List<String> parsePhotos(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .filter(photo -> !photo.isBlank())
                .map(photo -> mediaUrlService.resolveUrl(photo.trim()))
                .filter(photo -> photo != null && !photo.isBlank())
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
