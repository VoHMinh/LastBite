package com.LastBite.modules.store.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.bag.dto.response.PublicBagSummaryResponse;
import com.LastBite.modules.bag.service.BagDiscoveryServicePort;
import com.LastBite.modules.store.dto.response.PublicStoreDetailResponse;
import com.LastBite.modules.store.dto.response.StoreResponse;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.store.service.StoreQueryServicePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
@Tag(name = "Public Stores", description = "Tra cứu cửa hàng công khai")
public class StorePublicController {

    private final StoreQueryServicePort storeQueryService;
    private final BagDiscoveryServicePort bagDiscoveryService;

    @GetMapping
    @Operation(summary = "Tìm kiếm cửa hàng đang hoạt động và đã xác minh")
    public ResponseEntity<ApiResponse<Page<StoreResponse>>> searchStores(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) StoreCategory category,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return ResponseEntity.ok(ApiResponse.ok(
                storeQueryService.searchStores(keyword, category, city, district, pageable)));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Lấy chi tiết cửa hàng công khai theo slug")
    public ResponseEntity<ApiResponse<PublicStoreDetailResponse>> getStoreBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(storeQueryService.getStoreBySlug(slug)));
    }

    @GetMapping("/{storeId}/bags")
    @Operation(summary = "Lấy danh sách túi hôm nay của một cửa hàng")
    public ResponseEntity<ApiResponse<List<PublicBagSummaryResponse>>> getStoreBags(
            @PathVariable UUID storeId,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok(bagDiscoveryService.storeBags(storeId, limit)));
    }
}
