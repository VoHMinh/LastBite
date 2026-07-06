package com.LastBite.modules.user.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.media.service.MediaUrlService;
import com.LastBite.modules.store.dto.response.StoreResponse;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.repository.StoreRepository;
import com.LastBite.modules.user.entity.FavoriteStore;
import com.LastBite.modules.user.repository.FavoriteStoreRepository;
import com.LastBite.modules.user.repository.UserDiscoveryPreferenceRepository;
import com.LastBite.modules.user.service.FavoriteStoreServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriteStoreService implements FavoriteStoreServicePort {

    private final FavoriteStoreRepository favoriteStoreRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final MediaUrlService mediaUrlService;
    private final UserDiscoveryPreferenceRepository discoveryPreferenceRepository;

    @Transactional(readOnly = true)
    public List<StoreResponse> list(UUID userId, Double lat, Double lng) {
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
        return favoriteStoreRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(FavoriteStore::getStore)
                .map(store -> toResponse(store, resolvedLat, resolvedLng))
                .toList();
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "home-discovery"}, allEntries = true)
    public StoreResponse add(UUID userId, UUID storeId) {
        return favoriteStoreRepository.findByUserIdAndStoreId(userId, storeId)
                .map(FavoriteStore::getStore)
                .map(store -> toResponse(store, null, null))
                .orElseGet(() -> createFavorite(userId, storeId));
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "home-discovery"}, allEntries = true)
    public void delete(UUID userId, UUID storeId) {
        favoriteStoreRepository.deleteByUserIdAndStoreId(userId, storeId);
    }

    private StoreResponse createFavorite(UUID userId, UUID storeId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));

        favoriteStoreRepository.save(FavoriteStore.builder()
                .user(user)
                .store(store)
                .build());
        return toResponse(store, null, null);
    }

    private StoreResponse toResponse(Store store, Double userLat, Double userLng) {
        Double distanceKm = computeDistance(store.getLat(), store.getLng(), userLat, userLng);
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
                .distanceKm(distanceKm)
                .coverImageUrl(mediaUrlService.resolveUrl(store.getCoverImageKey(), store.getCoverImageUrl()))
                .logoUrl(mediaUrlService.resolveUrl(store.getLogoKey(), store.getLogoUrl()))
                .status(store.getStatus())
                .verificationStatus(store.getVerificationStatus())
                .avgRating(store.getAvgRating())
                .totalRatings(store.getTotalRatings())
                .createdAt(store.getCreatedAt())
                .build();
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
