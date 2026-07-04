package com.LastBite.modules.user.service;

import com.LastBite.modules.store.dto.response.StoreResponse;

import java.util.List;
import java.util.UUID;

public interface FavoriteStoreServicePort {

    List<StoreResponse> list(UUID userId, Double lat, Double lng);

    StoreResponse add(UUID userId, UUID storeId);

    void delete(UUID userId, UUID storeId);
}
