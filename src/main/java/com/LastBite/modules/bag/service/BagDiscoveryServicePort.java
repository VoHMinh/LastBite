package com.LastBite.modules.bag.service;

import com.LastBite.modules.bag.dto.response.PublicBagDetailResponse;
import com.LastBite.modules.bag.dto.response.PublicBagSummaryResponse;
import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.store.enums.StoreCategory;

import java.util.List;
import java.util.UUID;

public interface BagDiscoveryServicePort {

    List<PublicBagSummaryResponse> today(UUID userId, Double lat, Double lng, Double radiusKm, DietType dietType,
                                          BagType bagType, String sort, Integer limit);

    List<PublicBagSummaryResponse> discover(UUID userId, Double lat, Double lng, Double radiusKm,
                                             StoreCategory category, DietType dietType, BagType bagType,
                                             String district, String sort, Integer limit);

    List<PublicBagSummaryResponse> search(UUID userId, String keyword, Double lat, Double lng, Double radiusKm,
                                          StoreCategory category, DietType dietType, BagType bagType,
                                          String district, String sort, Integer limit);

    PublicBagDetailResponse detail(UUID bagId, UUID userId, Double lat, Double lng);

    List<PublicBagSummaryResponse> storeBags(UUID storeId, Integer limit);
}
