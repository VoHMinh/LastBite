package com.LastBite.modules.merchant.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.merchant.dto.request.CreateStoreMemberRequest;
import com.LastBite.modules.merchant.dto.response.StoreMemberResponse;
import com.LastBite.modules.merchant.service.MerchantMemberService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchant/stores/{storeId}/members")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MERCHANT_OWNER','MANAGER')")
public class MerchantMemberController {
    private final MerchantMemberService memberService;

    @PostMapping
    @Operation(operationId = "createStoreMember", summary = "Tạo tài khoản nhân viên cho cửa hàng")
    public ResponseEntity<ApiResponse<StoreMemberResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @Valid @RequestBody CreateStoreMemberRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                memberService.create(userId(jwt), storeId, request),
                "Tài khoản đã được tạo; mật khẩu tạm thời chỉ hiển thị trong phản hồi này"));
    }

    @GetMapping
    @Operation(operationId = "listStoreMembers", summary = "Lấy danh sách nhân viên của cửa hàng")
    public ResponseEntity<ApiResponse<List<StoreMemberResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.list(userId(jwt), storeId)));
    }

    @PatchMapping("/{memberId}/suspend")
    @Operation(operationId = "suspendStoreMember", summary = "Tạm ngưng tài khoản nhân viên")
    public ResponseEntity<ApiResponse<StoreMemberResponse>> suspend(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storeId,
            @PathVariable UUID memberId) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.suspend(userId(jwt), storeId, memberId)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
