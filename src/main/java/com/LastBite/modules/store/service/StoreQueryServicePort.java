package com.LastBite.modules.store.service;

import com.LastBite.modules.store.dto.response.PublicStoreDetailResponse;
import com.LastBite.modules.store.dto.response.StoreResponse;
import com.LastBite.modules.store.enums.StoreCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface StoreQueryServicePort {

    Page<StoreResponse> searchStores(String keyword, StoreCategory category, String city, String district,
                                     Pageable pageable);

    PublicStoreDetailResponse getStoreBySlug(String slug);

    PublicStoreDetailResponse getStoreById(UUID storeId);
}
