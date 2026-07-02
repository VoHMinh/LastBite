package com.LastBite.modules.promotion.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.promotion.dto.response.UserVoucherResponse;
import com.LastBite.modules.promotion.enums.UserVoucherStatus;
import com.LastBite.modules.promotion.service.VoucherApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/vouchers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class UserVoucherController {

    private final VoucherApplicationService voucherApplicationService;

    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(operationId = "listUserVouchers")
    public ResponseEntity<ApiResponse<PageResponse<UserVoucherResponse>>> wallet(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UserVoucherStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(voucherApplicationService.wallet(userId(jwt), status, pageable)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
