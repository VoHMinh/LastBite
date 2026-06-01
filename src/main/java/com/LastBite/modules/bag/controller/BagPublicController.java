package com.LastBite.modules.bag.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.bag.dto.response.PublicBagDetailResponse;
import com.LastBite.modules.bag.dto.response.PublicBagSummaryResponse;
import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.bag.service.BagDiscoveryService;
import com.LastBite.modules.store.enums.StoreCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bags")
@RequiredArgsConstructor
@Tag(name = "Túi bất ngờ (Công khai)", description = "Discovery và xem chi tiết túi hôm nay")
public class BagPublicController {

    private final BagDiscoveryService discoveryService;

    @GetMapping("/today")
    @Operation(summary = "Lấy danh sách túi hôm nay trong bán kính X km")
    public ResponseEntity<ApiResponse<List<PublicBagSummaryResponse>>> today(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false, defaultValue = "5") Double radius,
            @RequestParam(required = false) DietType dietType,
            @RequestParam(required = false) BagType bagType,
            @RequestParam(defaultValue = "pickup_time") String sort,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok(discoveryService.today(lat, lng, radius, dietType, bagType, sort, limit)));
    }

    @GetMapping("/nearby")
    @Operation(summary = "Tìm túi gần khách hoặc fallback theo quận")
    public ResponseEntity<ApiResponse<List<PublicBagSummaryResponse>>> nearby(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false, defaultValue = "5") Double radius,
            @RequestParam(required = false) StoreCategory category,
            @RequestParam(required = false) DietType dietType,
            @RequestParam(required = false) BagType bagType,
            @RequestParam(required = false) String district,
            @RequestParam(defaultValue = "pickup_time") String sort,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok(
                discoveryService.discover(lat, lng, radius, category, dietType, bagType, district, sort, limit)));
    }

    @GetMapping("/{bagId}")
    @Operation(summary = "Lấy chi tiết túi hôm nay")
    public ResponseEntity<ApiResponse<PublicBagDetailResponse>> detail(@PathVariable UUID bagId) {
        return ResponseEntity.ok(ApiResponse.ok(discoveryService.detail(bagId)));
    }
}
