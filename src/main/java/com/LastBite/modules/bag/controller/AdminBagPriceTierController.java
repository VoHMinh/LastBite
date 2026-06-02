package com.LastBite.modules.bag.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.bag.dto.admin.BagPriceTierRequest;
import com.LastBite.modules.bag.dto.admin.BagPriceTierResponse;
import com.LastBite.modules.bag.dto.admin.UpdateBagPriceTierRequest;
import com.LastBite.modules.bag.service.AdminBagPriceTierServicePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/bag-price-tiers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Bag Price Tiers", description = "Quản lý bảng giá túi bất ngờ do nền tảng cấu hình")
public class AdminBagPriceTierController {

    private final AdminBagPriceTierServicePort tierService;

    @GetMapping
    @Operation(summary = "Danh sách gói giá")
    public ResponseEntity<ApiResponse<PageResponse<BagPriceTierResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.ASC, "category").and(Sort.by(Sort.Direction.ASC, "bagSize")));
        return ResponseEntity.ok(ApiResponse.ok(tierService.list(pageable)));
    }

    @GetMapping("/{tierId}")
    @Operation(summary = "Chi tiết gói giá")
    public ResponseEntity<ApiResponse<BagPriceTierResponse>> get(@PathVariable UUID tierId) {
        return ResponseEntity.ok(ApiResponse.ok(tierService.get(tierId)));
    }

    @PostMapping
    @Operation(summary = "Tạo gói giá mới")
    public ResponseEntity<ApiResponse<BagPriceTierResponse>> create(
            @Valid @RequestBody BagPriceTierRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(tierService.create(request), "Đã tạo gói giá"));
    }

    @PatchMapping("/{tierId}")
    @Operation(summary = "Cập nhật gói giá")
    public ResponseEntity<ApiResponse<BagPriceTierResponse>> update(
            @PathVariable UUID tierId,
            @Valid @RequestBody UpdateBagPriceTierRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(tierService.update(tierId, request), "Đã cập nhật gói giá"));
    }

    @PatchMapping("/{tierId}/activate")
    @Operation(summary = "Kích hoạt gói giá")
    public ResponseEntity<ApiResponse<BagPriceTierResponse>> activate(@PathVariable UUID tierId) {
        return ResponseEntity.ok(ApiResponse.ok(tierService.activate(tierId), "Đã kích hoạt gói giá"));
    }

    @PatchMapping("/{tierId}/deactivate")
    @Operation(summary = "Tạm tắt gói giá")
    public ResponseEntity<ApiResponse<BagPriceTierResponse>> deactivate(@PathVariable UUID tierId) {
        return ResponseEntity.ok(ApiResponse.ok(tierService.deactivate(tierId), "Đã tạm tắt gói giá"));
    }
}
