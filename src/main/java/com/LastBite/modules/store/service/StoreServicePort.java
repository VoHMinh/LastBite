package com.LastBite.modules.store.service;

import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.store.dto.request.CreateStoreRequest;
import com.LastBite.modules.store.dto.request.ScheduleRequest;
import com.LastBite.modules.store.dto.request.UpdateStoreRequest;
import com.LastBite.modules.store.dto.request.RequestStoreChangesRequest;
import com.LastBite.modules.store.dto.response.StoreDetailResponse;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.enums.VerificationStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface StoreServicePort {

    Store createStoreInternal(User owner, CreateStoreRequest request);

    StoreDetailResponse createStore(UUID ownerId, CreateStoreRequest request);

    StoreDetailResponse getMyStore(UUID ownerId);

    List<StoreDetailResponse> listMyStores(UUID ownerId);

    StoreDetailResponse getStore(UUID ownerId, UUID storeId);

    StoreDetailResponse updateStore(UUID ownerId, UpdateStoreRequest request);

    StoreDetailResponse updateStore(UUID ownerId, UUID storeId, UpdateStoreRequest request);

    StoreDetailResponse updateSchedules(UUID ownerId, List<ScheduleRequest> requests);

    StoreDetailResponse updateSchedules(UUID ownerId, UUID storeId, List<ScheduleRequest> requests);

    StoreDetailResponse pauseStore(UUID ownerId);

    StoreDetailResponse pauseStore(UUID ownerId, UUID storeId);

    StoreDetailResponse activateStore(UUID ownerId);

    StoreDetailResponse activateStore(UUID ownerId, UUID storeId);

    StoreDetailResponse submitReview(UUID ownerId);

    StoreDetailResponse submitReview(UUID ownerId, UUID storeId);

    PageResponse<StoreDetailResponse> listStoresForReview(VerificationStatus verificationStatus, Pageable pageable);

    StoreDetailResponse getStoreForReview(UUID storeId);

    StoreDetailResponse approveStore(UUID storeId);

    StoreDetailResponse rejectStore(UUID storeId, String rejectionReason);

    StoreDetailResponse requestChanges(UUID adminId, UUID storeId, RequestStoreChangesRequest request);
}
