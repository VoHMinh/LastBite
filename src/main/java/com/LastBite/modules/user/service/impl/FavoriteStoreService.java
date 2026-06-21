package com.LastBite.modules.user.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.store.dto.response.StoreResponse;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.repository.StoreRepository;
import com.LastBite.modules.user.entity.FavoriteStore;
import com.LastBite.modules.user.repository.FavoriteStoreRepository;
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

    @Transactional(readOnly = true)
    public List<StoreResponse> list(UUID userId) {
        return favoriteStoreRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(FavoriteStore::getStore)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "home-discovery"}, allEntries = true)
    public StoreResponse add(UUID userId, UUID storeId) {
        return favoriteStoreRepository.findByUserIdAndStoreId(userId, storeId)
                .map(FavoriteStore::getStore)
                .map(this::toResponse)
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
        return toResponse(store);
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
}
