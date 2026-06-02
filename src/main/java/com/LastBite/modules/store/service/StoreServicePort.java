package com.LastBite.modules.store.service;

import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.store.dto.request.CreateStoreRequest;
import com.LastBite.modules.store.dto.request.ScheduleRequest;
import com.LastBite.modules.store.dto.request.UpdateStoreRequest;
import com.LastBite.modules.store.dto.response.StoreDetailResponse;
import com.LastBite.modules.store.entity.Store;

import java.util.List;
import java.util.UUID;

public interface StoreServicePort {

    Store createStoreInternal(User owner, CreateStoreRequest request);

    StoreDetailResponse getMyStore(UUID ownerId);

    StoreDetailResponse updateStore(UUID ownerId, UpdateStoreRequest request);

    StoreDetailResponse updateSchedules(UUID ownerId, List<ScheduleRequest> requests);

    StoreDetailResponse pauseStore(UUID ownerId);

    StoreDetailResponse activateStore(UUID ownerId);
}
