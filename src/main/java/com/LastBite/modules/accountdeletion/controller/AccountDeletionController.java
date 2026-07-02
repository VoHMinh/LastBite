package com.LastBite.modules.accountdeletion.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.accountdeletion.dto.request.CreateAccountDeletionRequest;
import com.LastBite.modules.accountdeletion.dto.request.FinalizeAccountDeletionRequest;
import com.LastBite.modules.accountdeletion.dto.request.PublicAccountDeletionRequest;
import com.LastBite.modules.accountdeletion.dto.request.ReviewAccountDeletionRequest;
import com.LastBite.modules.accountdeletion.dto.response.AccountDeletionEligibilityResponse;
import com.LastBite.modules.accountdeletion.dto.response.AccountDeletionResponse;
import com.LastBite.modules.accountdeletion.enums.AccountDeletionRequestStatus;
import com.LastBite.modules.accountdeletion.service.AccountDeletionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/account-deletion")
@RequiredArgsConstructor
public class AccountDeletionController {

    private final AccountDeletionService service;

    @GetMapping("/me/eligibility")
    public ResponseEntity<ApiResponse<AccountDeletionEligibilityResponse>> eligibility(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(service.eligibility(userId(jwt))));
    }

    @PostMapping("/me")
    public ResponseEntity<ApiResponse<AccountDeletionResponse>> requestAuthenticated(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) CreateAccountDeletionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.requestAuthenticated(userId(jwt), request),
                "Account deletion request created"));
    }

    @PostMapping("/me/cancel")
    public ResponseEntity<ApiResponse<AccountDeletionResponse>> cancel(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.cancel(userId(jwt)),
                "Account deletion request cancelled"));
    }

    @PostMapping("/public-requests")
    public ResponseEntity<ApiResponse<Void>> requestPublic(
            @Valid @RequestBody PublicAccountDeletionRequest request) {
        service.requestPublic(request);
        return ResponseEntity.ok(ApiResponse.ok(null,
                "If the account exists, a verification email will be sent"));
    }

    @GetMapping("/public-requests/verify")
    public ResponseEntity<ApiResponse<AccountDeletionResponse>> verifyPublic(@RequestParam String token) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.verifyPublic(token),
                "Account deletion request verified"));
    }

    @GetMapping("/public-requests/cancel")
    public ResponseEntity<ApiResponse<AccountDeletionResponse>> cancelPublic(@RequestParam String token) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.cancelPublic(token),
                "Account deletion request cancelled"));
    }

    @GetMapping("/admin/requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AccountDeletionResponse>>> searchAdmin(
            @RequestParam(required = false) AccountDeletionRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = service.searchAdmin(status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.ok(new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages())));
    }

    @PatchMapping("/admin/requests/{requestId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AccountDeletionResponse>> approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) ReviewAccountDeletionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.approve(userId(jwt), requestId, request),
                "Account deletion request approved"));
    }

    @PatchMapping("/admin/requests/{requestId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AccountDeletionResponse>> reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) ReviewAccountDeletionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.reject(userId(jwt), requestId, request),
                "Account deletion request rejected"));
    }

    @PatchMapping("/admin/requests/{requestId}/finalize")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AccountDeletionResponse>> finalize(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) FinalizeAccountDeletionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.finalize(userId(jwt), requestId, request),
                "Account deletion request finalized"));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
