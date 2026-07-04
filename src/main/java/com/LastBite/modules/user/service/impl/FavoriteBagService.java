package com.LastBite.modules.user.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.bag.dto.response.PublicBagSummaryResponse;
import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.bag.repository.BagDailyStockRepository;
import com.LastBite.modules.bag.repository.SurpriseBagRepository;
import com.LastBite.modules.bag.service.impl.BagPricingService;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.user.entity.FavoriteBag;
import com.LastBite.modules.user.repository.FavoriteBagRepository;
import com.LastBite.modules.user.repository.UserDiscoveryPreferenceRepository;
import com.LastBite.modules.user.service.FavoriteBagServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriteBagService implements FavoriteBagServicePort {

    private final FavoriteBagRepository favoriteBagRepository;
    private final SurpriseBagRepository bagRepository;
    private final UserRepository userRepository;
    private final UserDiscoveryPreferenceRepository discoveryPreferenceRepository;
    private final BagDailyStockRepository dailyStockRepository;
    private final BagPricingService pricingService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<PublicBagSummaryResponse> list(UUID userId, Double lat, Double lng) {
        final Double resolvedLat;
        final Double resolvedLng;
        if (lat != null && lng != null) {
            resolvedLat = lat;
            resolvedLng = lng;
        } else {
            resolvedLat = discoveryPreferenceRepository.findByUserId(userId)
                    .flatMap(pref -> pref.getDefaultLat() != null ? java.util.Optional.of(pref.getDefaultLat()) : java.util.Optional.empty())
                    .orElse(null);
            resolvedLng = discoveryPreferenceRepository.findByUserId(userId)
                    .flatMap(pref -> pref.getDefaultLng() != null ? java.util.Optional.of(pref.getDefaultLng()) : java.util.Optional.empty())
                    .orElse(null);
        }
        return favoriteBagRepository.findByUserIdWithBagAndStore(userId).stream()
                .map(FavoriteBag::getBag)
                .map(bag -> toSummaryResponse(bag, resolvedLat, resolvedLng))
                .toList();
    }

    @Override
    @Transactional
    public PublicBagSummaryResponse add(UUID userId, UUID bagId) {
        if (favoriteBagRepository.existsByUserIdAndBagId(userId, bagId)) {
            SurpriseBag bag = bagRepository.findById(bagId)
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Bag not found"));
            return toSummaryResponse(bag, null, null);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        SurpriseBag bag = bagRepository.findById(bagId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Bag not found"));

        FavoriteBag favorite = FavoriteBag.builder()
                .id(new FavoriteBag.FavoriteBagId(userId, bagId))
                .user(user)
                .bag(bag)
                .build();
        favoriteBagRepository.save(favorite);
        return toSummaryResponse(bag, null, null);
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID bagId) {
        favoriteBagRepository.deleteByUserIdAndBagId(userId, bagId);
    }

    private PublicBagSummaryResponse toSummaryResponse(SurpriseBag bag, Double userLat, Double userLng) {
        Store store = bag.getStore();
        Double distanceKm = computeDistance(store.getLat(), store.getLng(), userLat, userLng);

        BagPricingService.PriceSnapshot price = resolvePrice(bag);

        return PublicBagSummaryResponse.builder()
                .bagId(bag.getId())
                .storeId(store.getId())
                .storeName(store.getName())
                .storeSlug(store.getSlug())
                .storeAddress(store.getAddress())
                .storeLogoUrl(store.getLogoUrl())
                .storeCoverImageUrl(store.getCoverImageUrl())
                .storeAvgRating(store.getAvgRating())
                .storeTotalRatings(store.getTotalRatings())
                .favoriteStore(false)
                .district(store.getDistrict())
                .city(store.getCity())
                .lat(store.getLat())
                .lng(store.getLng())
                .name(bag.getName())
                .description(bag.getDescription())
                .bagType(bag.getBagType())
                .dietType(bag.getDietType())
                .category(bag.getCategory())
                .bagSize(bag.getBagSize())
                .photos(bag.getPhotos() != null ? List.of(bag.getPhotos()) : List.of())
                .minimumValue(bag.getMinimumValue())
                .baseSalePrice(bag.getBaseSalePrice())
                .currentSalePrice(price.currentSalePrice())
                .savingsAmount(price.savingsAmount())
                .currentDiscountPercent(price.currentDiscountPercent())
                .dynamicMinPrice(bag.getDynamicMinPrice())
                .dynamicMaxPrice(bag.getDynamicMaxPrice())
                .dynamicPricingEnabled(bag.isDynamicPricingEnabled())
                .platformFee(bag.getPlatformFee())
                .maxPerOrder(bag.getMaxPerOrder())
                .containerProvided(bag.isContainerProvided())
                .carrierBagProvided(bag.isCarrierBagProvided())
                .packagingNote(bag.getPackagingNote())
                .pickupStartTime(bag.getPickupStartTime())
                .pickupEndTime(bag.getPickupEndTime())
                .distanceKm(distanceKm)
                .build();
    }

    private BagPricingService.PriceSnapshot resolvePrice(SurpriseBag bag) {
        LocalDate today = LocalDate.now(clock);
        return dailyStockRepository.findByBagIdAndDate(bag.getId(), today)
                .map(stock -> pricingService.currentPrice(bag, stock.getDate()))
                .orElseGet(() -> pricingService.currentPrice(
                        bag.getMinimumValue(),
                        bag.getBaseSalePrice(),
                        bag.getDynamicMinPrice(),
                        bag.getDynamicMaxPrice(),
                        false,
                        today,
                        bag.getPickupEndTime()));
    }

    private Double computeDistance(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return null;
        }
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(6371 * c * 100.0) / 100.0;
    }
}
