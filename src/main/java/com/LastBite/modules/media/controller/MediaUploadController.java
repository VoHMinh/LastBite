package com.LastBite.modules.media.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.media.dto.request.ConfirmMediaUploadRequest;
import com.LastBite.modules.media.dto.request.CreatePresignedUploadRequest;
import com.LastBite.modules.media.dto.response.MediaUploadResponse;
import com.LastBite.modules.media.dto.response.MediaAccessUrlResponse;
import com.LastBite.modules.media.dto.response.PresignedUploadResponse;
import com.LastBite.modules.media.service.MediaUploadServicePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media/uploads")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Media Uploads", description = "Tạo pre-signed URL để upload media trực tiếp lên S3")
public class MediaUploadController {

    private final MediaUploadServicePort mediaUploadService;

    @PostMapping("/presigned-url")
    @Operation(summary = "Tạo S3 pre-signed PUT URL")
    public ResponseEntity<ApiResponse<PresignedUploadResponse>> createPresignedUploadUrl(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePresignedUploadRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                mediaUploadService.createPresignedUploadUrl(extractUserId(jwt), request)));
    }

    @PostMapping("/confirm")
    @Operation(summary = "Xác nhận media đã upload lên S3")
    public ResponseEntity<ApiResponse<MediaUploadResponse>> confirmUpload(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConfirmMediaUploadRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                mediaUploadService.confirmUpload(extractUserId(jwt), request), "Đã xác nhận upload"));
    }

    @GetMapping("/{uploadId}/access-url")
    @Operation(summary = "Tao signed URL ngan han cho tai lieu private")
    public ResponseEntity<ApiResponse<MediaAccessUrlResponse>> accessUrl(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID uploadId) {
        return ResponseEntity.ok(ApiResponse.ok(
                mediaUploadService.createPrivateAccessUrl(extractUserId(jwt), uploadId)));
    }

    private UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }
}
