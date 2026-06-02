package com.LastBite.modules.bag.service;

import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.bag.dto.admin.BagPriceTierRequest;
import com.LastBite.modules.bag.dto.admin.BagPriceTierResponse;
import com.LastBite.modules.bag.dto.admin.UpdateBagPriceTierRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminBagPriceTierServicePort {

    PageResponse<BagPriceTierResponse> list(Pageable pageable);

    BagPriceTierResponse get(UUID tierId);

    BagPriceTierResponse create(BagPriceTierRequest request);

    BagPriceTierResponse update(UUID tierId, UpdateBagPriceTierRequest request);

    BagPriceTierResponse activate(UUID tierId);

    BagPriceTierResponse deactivate(UUID tierId);
}
