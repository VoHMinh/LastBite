package com.LastBite.modules.user.service;

import com.LastBite.modules.bag.dto.response.PublicBagSummaryResponse;

import java.util.List;
import java.util.UUID;

public interface FavoriteBagServicePort {

    List<PublicBagSummaryResponse> list(UUID userId);

    PublicBagSummaryResponse add(UUID userId, UUID bagId);

    void delete(UUID userId, UUID bagId);
}
