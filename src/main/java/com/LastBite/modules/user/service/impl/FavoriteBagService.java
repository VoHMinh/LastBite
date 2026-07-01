package com.LastBite.modules.user.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.bag.dto.response.PublicBagSummaryResponse;
import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.bag.repository.SurpriseBagRepository;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.user.entity.FavoriteBag;
import com.LastBite.modules.user.repository.FavoriteBagRepository;
import com.LastBite.modules.user.service.FavoriteBagServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriteBagService implements FavoriteBagServicePort {

    private final FavoriteBagRepository favoriteBagRepository;
    private final SurpriseBagRepository bagRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PublicBagSummaryResponse> list(UUID userId) {
        return favoriteBagRepository.findByUserIdWithBagAndStore(userId).stream()
                .map(FavoriteBag::getBag)
                .map(this::toSummaryResponse)
                .toList();
    }

    @Override
    @Transactional
    public PublicBagSummaryResponse add(UUID userId, UUID bagId) {
        if (favoriteBagRepository.existsByUserIdAndBagId(userId, bagId)) {
            SurpriseBag bag = bagRepository.findById(bagId)
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Bag not found"));
            return toSummaryResponse(bag);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        SurpriseBag bag = bagRepository.findById(bagId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Bag not found"));

        FavoriteBag favorite = FavoriteBag.builder()
                .user(user)
                .bag(bag)
                .build();
        favorite.getId().setUserId(userId);
        favorite.getId().setBagId(bagId);
        favoriteBagRepository.save(favorite);
        return toSummaryResponse(bag);
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID bagId) {
        favoriteBagRepository.deleteByUserIdAndBagId(userId, bagId);
    }

    private PublicBagSummaryResponse toSummaryResponse(SurpriseBag bag) {
        Store store = bag.getStore();
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
                .build();
    }
}
