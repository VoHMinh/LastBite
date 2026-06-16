package com.LastBite.modules.store.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.merchant.service.StoreAccessService;
import com.LastBite.modules.store.dto.request.StoreClosureDayRequest;
import com.LastBite.modules.store.dto.request.StoreSpecialHoursRequest;
import com.LastBite.modules.store.dto.response.StoreClosureDayResponse;
import com.LastBite.modules.store.dto.response.StoreSpecialHoursResponse;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.service.StoreCalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchant/stores/{storeId}/calendar")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MERCHANT_OWNER','MANAGER')")
public class MerchantStoreCalendarController {

    private static final Set<UserRole> CALENDAR_ROLES = Set.of(UserRole.MANAGER);

    private final StoreAccessService storeAccessService;
    private final StoreCalendarService calendarService;

    @GetMapping("/closures")
    public ResponseEntity<ApiResponse<List<StoreClosureDayResponse>>> closures(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Store store = requireStore(jwt, storeId);
        return ResponseEntity.ok(ApiResponse.ok(calendarService.listClosures(store, from, to)));
    }

    @PutMapping("/closures")
    public ResponseEntity<ApiResponse<StoreClosureDayResponse>> upsertClosure(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @Valid @RequestBody StoreClosureDayRequest request) {
        Store store = requireStore(jwt, storeId);
        return ResponseEntity.ok(ApiResponse.ok(
                calendarService.upsertClosure(userId(jwt), store, request),
                "Da cap nhat ngay nghi"));
    }

    @DeleteMapping("/closures/{closedDate}")
    public ResponseEntity<ApiResponse<Void>> deleteClosure(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closedDate) {
        Store store = requireStore(jwt, storeId);
        calendarService.deleteClosure(store, closedDate);
        return ResponseEntity.ok(ApiResponse.ok(null, "Da xoa ngay nghi"));
    }

    @GetMapping("/special-hours")
    public ResponseEntity<ApiResponse<List<StoreSpecialHoursResponse>>> specialHours(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Store store = requireStore(jwt, storeId);
        return ResponseEntity.ok(ApiResponse.ok(calendarService.listSpecialHours(store, from, to)));
    }

    @PutMapping("/special-hours")
    public ResponseEntity<ApiResponse<StoreSpecialHoursResponse>> upsertSpecialHours(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @Valid @RequestBody StoreSpecialHoursRequest request) {
        Store store = requireStore(jwt, storeId);
        return ResponseEntity.ok(ApiResponse.ok(
                calendarService.upsertSpecialHours(userId(jwt), store, request),
                "Da cap nhat gio dac biet"));
    }

    @DeleteMapping("/special-hours/{specialDate}")
    public ResponseEntity<ApiResponse<Void>> deleteSpecialHours(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate specialDate) {
        Store store = requireStore(jwt, storeId);
        calendarService.deleteSpecialHours(store, specialDate);
        return ResponseEntity.ok(ApiResponse.ok(null, "Da xoa gio dac biet"));
    }

    private Store requireStore(Jwt jwt, UUID storeId) {
        return storeAccessService.require(userId(jwt), storeId, CALENDAR_ROLES);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
