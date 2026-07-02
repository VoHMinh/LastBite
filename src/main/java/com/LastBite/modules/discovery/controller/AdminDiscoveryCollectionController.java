package com.LastBite.modules.discovery.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.discovery.dto.request.DiscoveryCollectionItemRequest;
import com.LastBite.modules.discovery.dto.request.DiscoveryCollectionRequest;
import com.LastBite.modules.discovery.dto.request.UpdateDiscoveryCollectionItemRequest;
import com.LastBite.modules.discovery.dto.request.UpdateDiscoveryCollectionRequest;
import com.LastBite.modules.discovery.dto.response.DiscoveryCollectionItemResponse;
import com.LastBite.modules.discovery.dto.response.DiscoveryCollectionResponse;
import com.LastBite.modules.discovery.service.AdminDiscoveryCollectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/discovery-collections")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Discovery Collections", description = "Quan ly curated collections tren Home")
public class AdminDiscoveryCollectionController {

    private final AdminDiscoveryCollectionService collectionService;

    @GetMapping
    @Operation(operationId = "listDiscoveryCollections", summary = "Danh sach discovery collections")
    public ResponseEntity<ApiResponse<PageResponse<DiscoveryCollectionResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.ASC, "displayOrder").and(Sort.by(Sort.Direction.ASC, "slug")));
        return ResponseEntity.ok(ApiResponse.ok(collectionService.list(pageable)));
    }

    @GetMapping("/{collectionId}")
    @Operation(summary = "Chi tiet discovery collection")
    public ResponseEntity<ApiResponse<DiscoveryCollectionResponse>> get(@PathVariable UUID collectionId) {
        return ResponseEntity.ok(ApiResponse.ok(collectionService.get(collectionId)));
    }

    @PostMapping
    @Operation(summary = "Tao discovery collection")
    public ResponseEntity<ApiResponse<DiscoveryCollectionResponse>> create(
            @Valid @RequestBody DiscoveryCollectionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(collectionService.create(request), "Created discovery collection"));
    }

    @PatchMapping("/{collectionId}")
    @Operation(summary = "Cap nhat discovery collection")
    public ResponseEntity<ApiResponse<DiscoveryCollectionResponse>> update(
            @PathVariable UUID collectionId,
            @Valid @RequestBody UpdateDiscoveryCollectionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(collectionService.update(collectionId, request),
                "Updated discovery collection"));
    }

    @PatchMapping("/{collectionId}/activate")
    @Operation(summary = "Kich hoat discovery collection")
    public ResponseEntity<ApiResponse<DiscoveryCollectionResponse>> activate(@PathVariable UUID collectionId) {
        return ResponseEntity.ok(ApiResponse.ok(collectionService.activate(collectionId),
                "Activated discovery collection"));
    }

    @PatchMapping("/{collectionId}/deactivate")
    @Operation(summary = "Tat discovery collection")
    public ResponseEntity<ApiResponse<DiscoveryCollectionResponse>> deactivate(@PathVariable UUID collectionId) {
        return ResponseEntity.ok(ApiResponse.ok(collectionService.deactivate(collectionId),
                "Deactivated discovery collection"));
    }

    @GetMapping("/{collectionId}/items")
    @Operation(summary = "Danh sach manual items cua collection")
    public ResponseEntity<ApiResponse<List<DiscoveryCollectionItemResponse>>> listItems(
            @PathVariable UUID collectionId) {
        return ResponseEntity.ok(ApiResponse.ok(collectionService.listItems(collectionId)));
    }

    @PostMapping("/{collectionId}/items")
    @Operation(summary = "Them bag vao manual collection")
    public ResponseEntity<ApiResponse<DiscoveryCollectionItemResponse>> addItem(
            @PathVariable UUID collectionId,
            @Valid @RequestBody DiscoveryCollectionItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(collectionService.addItem(collectionId, request),
                "Added collection item"));
    }

    @PatchMapping("/{collectionId}/items/{bagId}")
    @Operation(summary = "Cap nhat pinned order cua manual item")
    public ResponseEntity<ApiResponse<DiscoveryCollectionItemResponse>> updateItem(
            @PathVariable UUID collectionId,
            @PathVariable UUID bagId,
            @Valid @RequestBody UpdateDiscoveryCollectionItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(collectionService.updateItem(collectionId, bagId, request),
                "Updated collection item"));
    }

    @DeleteMapping("/{collectionId}/items/{bagId}")
    @Operation(summary = "Xoa bag khoi manual collection")
    public ResponseEntity<ApiResponse<Void>> deleteItem(@PathVariable UUID collectionId,
                                                        @PathVariable UUID bagId) {
        collectionService.deleteItem(collectionId, bagId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
