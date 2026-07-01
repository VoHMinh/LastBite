package com.LastBite.modules.user.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.auth.dto.response.UserResponse;
import com.LastBite.modules.user.dto.request.AddressRequest;
import com.LastBite.modules.user.dto.request.ChangePasswordRequest;
import com.LastBite.modules.user.dto.request.UpdateDiscoveryPreferenceRequest;
import com.LastBite.modules.user.dto.request.UpdateProfileRequest;
import com.LastBite.modules.user.dto.response.AddressResponse;
import com.LastBite.modules.user.dto.response.DiscoveryPreferenceResponse;
import com.LastBite.modules.user.dto.response.UserImpactResponse;
import com.LastBite.modules.user.service.AddressServicePort;
import com.LastBite.modules.user.service.DiscoveryPreferenceServicePort;
import com.LastBite.modules.user.service.FavoriteBagServicePort;
import com.LastBite.modules.user.service.FavoriteStoreServicePort;
import com.LastBite.modules.user.service.ImpactCalculationService;
import com.LastBite.modules.user.service.UserServicePort;
import com.LastBite.modules.bag.dto.response.PublicBagSummaryResponse;
import com.LastBite.modules.store.dto.response.StoreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "Quản lý hồ sơ cá nhân và địa chỉ")
public class UserController {

    private final UserServicePort userService;
    private final AddressServicePort addressService;
    private final FavoriteStoreServicePort favoriteStoreService;
    private final FavoriteBagServicePort favoriteBagService;
    private final DiscoveryPreferenceServicePort discoveryPreferenceService;
    private final ImpactCalculationService impactCalculationService;

    // ── Profile ──

    @GetMapping("/me")
    @Operation(summary = "Lấy hồ sơ cá nhân")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(userId)));
    }

    @PutMapping("/me")
    @Operation(summary = "Cập nhật hồ sơ cá nhân")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(userId, request), "Cập nhật thành công"));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Đổi mật khẩu")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = extractUserId(jwt);
        userService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── Addresses ──

    @GetMapping("/me/discovery-preferences")
    @Operation(summary = "Get saved discovery location and preferences")
    public ResponseEntity<ApiResponse<DiscoveryPreferenceResponse>> getDiscoveryPreferences(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(discoveryPreferenceService.get(userId)));
    }

    @PutMapping("/me/discovery-preferences")
    @Operation(summary = "Update saved discovery location and preferences")
    public ResponseEntity<ApiResponse<DiscoveryPreferenceResponse>> updateDiscoveryPreferences(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateDiscoveryPreferenceRequest request) {
        UUID userId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(discoveryPreferenceService.update(userId, request),
                "Discovery preferences updated"));
    }

    @PostMapping("/me/discovery-preferences/skip-onboarding")
    @Operation(summary = "Skip discovery preference onboarding")
    public ResponseEntity<ApiResponse<DiscoveryPreferenceResponse>> skipDiscoveryPreferenceOnboarding(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(discoveryPreferenceService.skipOnboarding(userId),
                "Discovery onboarding skipped"));
    }

    @GetMapping("/me/addresses")
    @Operation(summary = "Danh sách địa chỉ giao hàng")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getAddresses(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(addressService.getAddresses(userId)));
    }

    @PostMapping("/me/addresses")
    @Operation(summary = "Thêm địa chỉ mới")
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddressRequest request) {
        UUID userId = extractUserId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(addressService.addAddress(userId, request), "Thêm địa chỉ thành công"));
    }

    @PutMapping("/me/addresses/{addressId}")
    @Operation(summary = "Cập nhật địa chỉ")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID addressId,
            @Valid @RequestBody AddressRequest request) {
        UUID userId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(addressService.updateAddress(userId, addressId, request)));
    }

    @DeleteMapping("/me/addresses/{addressId}")
    @Operation(summary = "Xóa địa chỉ")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID addressId) {
        UUID userId = extractUserId(jwt);
        addressService.deleteAddress(userId, addressId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PatchMapping("/me/addresses/{addressId}/default")
    @Operation(summary = "Đặt địa chỉ mặc định")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefault(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID addressId) {
        UUID userId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(addressService.setDefault(userId, addressId)));
    }

    // â”€â”€ Favorite Stores â”€â”€

    @GetMapping("/me/favorite-stores")
    @Operation(summary = "Danh sÃ¡ch cá»­a hÃ ng yÃªu thÃ­ch")
    public ResponseEntity<ApiResponse<List<StoreResponse>>> getFavoriteStores(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(favoriteStoreService.list(userId)));
    }

    @PostMapping("/me/favorite-stores/{storeId}")
    @Operation(summary = "ThÃªm cá»­a hÃ ng vÃ o danh sÃ¡ch yÃªu thÃ­ch")
    public ResponseEntity<ApiResponse<StoreResponse>> addFavoriteStore(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId) {
        UUID userId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(favoriteStoreService.add(userId, storeId),
                "ÄÃ£ thÃªm cá»­a hÃ ng yÃªu thÃ­ch"));
    }

    @DeleteMapping("/me/favorite-stores/{storeId}")
    @Operation(summary = "XÃ³a cá»­a hÃ ng khá»i danh sÃ¡ch yÃªu thÃ­ch")
    public ResponseEntity<ApiResponse<Void>> deleteFavoriteStore(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId) {
        UUID userId = extractUserId(jwt);
        favoriteStoreService.delete(userId, storeId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── Favorite Bags ──

    @GetMapping("/me/favorite-bags")
    @Operation(summary = "Danh sach tui yeu thich")
    public ResponseEntity<ApiResponse<List<PublicBagSummaryResponse>>> getFavoriteBags(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(favoriteBagService.list(userId)));
    }

    @PostMapping("/me/favorite-bags/{bagId}")
    @Operation(summary = "Them tui vao danh sach yeu thich")
    public ResponseEntity<ApiResponse<PublicBagSummaryResponse>> addFavoriteBag(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bagId) {
        UUID userId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(favoriteBagService.add(userId, bagId),
                "Da them tui yeu thich"));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me/favorite-bags/{bagId}")
    @Operation(summary = "Xoa tui khoi danh sach yeu thich")
    public void deleteFavoriteBag(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID bagId) {
        UUID userId = extractUserId(jwt);
        favoriteBagService.delete(userId, bagId);
    }

    // ── Impact ──

    @GetMapping("/me/impact")
    @Operation(summary = "Lay thong ke tac dong moi truong cua nguoi dung")
    public ResponseEntity<ApiResponse<UserImpactResponse>> getImpact(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        return ResponseEntity.ok(ApiResponse.ok(impactCalculationService.calculate(userId)));
    }

    private UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
