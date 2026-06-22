package com.LastBite.modules.bag.service;

import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.bag.dto.request.AdjustTodayStockRequest;
import com.LastBite.modules.bag.dto.request.CreateSurpriseBagRequest;
import com.LastBite.modules.bag.dto.request.SetDailyStockRequest;
import com.LastBite.modules.bag.dto.request.UpdateSurpriseBagRequest;
import com.LastBite.modules.bag.dto.response.BagPriceTierSummaryResponse;
import com.LastBite.modules.bag.dto.response.DailyStockResponse;
import com.LastBite.modules.bag.dto.response.StockAuditLogResponse;
import com.LastBite.modules.bag.dto.response.SurpriseBagResponse;
import com.LastBite.modules.store.enums.StoreCategory;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SurpriseBagServicePort {

    SurpriseBagResponse create(UUID ownerId, CreateSurpriseBagRequest request);

    SurpriseBagResponse create(UUID actorId, UUID storeId, CreateSurpriseBagRequest request);

    PageResponse<SurpriseBagResponse> list(UUID ownerId, Pageable pageable);

    PageResponse<SurpriseBagResponse> list(UUID actorId, UUID storeId, Pageable pageable);

    SurpriseBagResponse update(UUID ownerId, UUID bagId, UpdateSurpriseBagRequest request);

    void softDelete(UUID ownerId, UUID bagId);

    SurpriseBagResponse pause(UUID ownerId, UUID bagId);

    SurpriseBagResponse resume(UUID ownerId, UUID bagId);

    DailyStockResponse setStock(UUID ownerId, UUID bagId, LocalDate date, SetDailyStockRequest request);

    DailyStockResponse adjustTodayStock(UUID ownerId, UUID bagId, AdjustTodayStockRequest request);

    PageResponse<StockAuditLogResponse> auditLogs(UUID ownerId, UUID bagId, Pageable pageable);

    List<BagPriceTierSummaryResponse> listActivePriceTiers(StoreCategory category);

    List<StoreCategory> listAvailableCategories();

    int createTodayStocks();

    int expireUnsoldStocks();
}
