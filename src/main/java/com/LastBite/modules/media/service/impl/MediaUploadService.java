package com.LastBite.modules.media.service.impl;

import com.LastBite.common.config.AwsS3Properties;
import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.media.dto.request.ConfirmMediaUploadRequest;
import com.LastBite.modules.media.dto.request.CreatePresignedUploadRequest;
import com.LastBite.modules.media.dto.response.MediaUploadResponse;
import com.LastBite.modules.media.dto.response.PresignedUploadResponse;
import com.LastBite.modules.media.entity.MediaUpload;
import com.LastBite.modules.media.enums.MediaPurpose;
import com.LastBite.modules.media.enums.MediaTargetType;
import com.LastBite.modules.media.enums.MediaType;
import com.LastBite.modules.media.enums.MediaUploadStatus;
import com.LastBite.modules.media.repository.MediaUploadRepository;
import com.LastBite.modules.media.service.MediaStorageServicePort;
import com.LastBite.modules.media.service.MediaUploadServicePort;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaUploadService implements MediaUploadServicePort {

    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> VIDEO_CONTENT_TYPES = Set.of("video/mp4", "video/webm", "video/quicktime");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "video/mp4", "mp4",
            "video/webm", "webm",
            "video/quicktime", "mov"
    );

    private final MediaUploadRepository mediaUploadRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final MediaStorageServicePort storageService;
    private final AwsS3Properties s3Properties;
    private final Clock clock;

    @Override
    @Transactional
    public PresignedUploadResponse createPresignedUploadUrl(UUID ownerId, CreatePresignedUploadRequest request) {
        User owner = getUser(ownerId);
        String contentType = normalizeContentType(request.getContentType());
        validatePurposeAccess(owner, request.getPurpose());
        validateMedia(request.getPurpose(), contentType, request.getFileSize());

        String key = objectKey(ownerId, request.getPurpose(), contentType);
        String publicUrl = s3Properties.publicUrl(key);
        MediaTarget target = resolveTarget(ownerId, request);

        MediaUpload upload = MediaUpload.builder()
                .owner(owner)
                .purpose(request.getPurpose())
                .mediaType(request.getPurpose().mediaType())
                .targetType(target.targetType())
                .targetId(target.targetId())
                .bucket(s3Properties.bucketName())
                .objectKey(key)
                .publicUrl(publicUrl)
                .contentType(contentType)
                .fileSize(request.getFileSize())
                .status(MediaUploadStatus.PENDING)
                .build();
        upload = mediaUploadRepository.save(upload);

        String uploadUrl = storageService.createPresignedPutUrl(
                s3Properties.bucketName(),
                key,
                contentType,
                Duration.ofSeconds(s3Properties.uploadExpireSeconds()));

        log.info("Đã tạo presigned upload {} cho user {} với purpose {}", upload.getId(), ownerId, request.getPurpose());
        return PresignedUploadResponse.builder()
                .uploadId(upload.getId())
                .uploadUrl(uploadUrl)
                .key(key)
                .publicUrl(publicUrl)
                .expiresInSeconds(s3Properties.uploadExpireSeconds())
                .build();
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = {"store-detail", "store-by-slug", "store-list"}, allEntries = true),
            @CacheEvict(value = "user-profile", key = "#ownerId")
    })
    public MediaUploadResponse confirmUpload(UUID ownerId, ConfirmMediaUploadRequest request) {
        MediaUpload upload = mediaUploadRepository.findByIdAndOwnerId(request.getUploadId(), ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy upload"));

        String key = request.getKey().trim();
        if (!upload.getObjectKey().equals(key) || !key.startsWith(ownerPrefix(ownerId, upload.getPurpose()))) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Object key không thuộc upload của người dùng hiện tại");
        }
        if (upload.getStatus() == MediaUploadStatus.CONFIRMED) {
            return toResponse(upload);
        }
        if (!storageService.objectExists(upload.getBucket(), upload.getObjectKey())) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Object chưa tồn tại trên S3");
        }

        upload.setStatus(MediaUploadStatus.CONFIRMED);
        upload.setConfirmedAt(Instant.now(clock));
        upload = mediaUploadRepository.save(upload);

        applyConfirmedUpload(ownerId, upload);
        log.info("Đã confirm media upload {} cho user {}", upload.getId(), ownerId);
        return toResponse(upload);
    }

    private void validatePurposeAccess(User owner, MediaPurpose purpose) {
        if (purpose.isStorePurpose() && owner.getRole() != UserRole.STORE_OWNER) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Chỉ chủ cửa hàng được upload ảnh cửa hàng");
        }
    }

    private void validateMedia(MediaPurpose purpose, String contentType, long fileSize) {
        boolean isImage = IMAGE_CONTENT_TYPES.contains(contentType);
        boolean isVideo = VIDEO_CONTENT_TYPES.contains(contentType);
        if (purpose.mediaType() == MediaType.IMAGE && !isImage) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Chỉ hỗ trợ image/jpeg, image/png, image/webp");
        }
        if (purpose.mediaType() == MediaType.VIDEO && !isVideo) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Chỉ hỗ trợ video/mp4, video/webm, video/quicktime");
        }

        long maxBytes = purpose.mediaType() == MediaType.IMAGE
                ? s3Properties.maxImageSizeBytes()
                : s3Properties.maxVideoSizeBytes();
        if (fileSize > maxBytes) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "File vượt quá dung lượng cho phép");
        }
    }

    private MediaTarget resolveTarget(UUID ownerId, CreatePresignedUploadRequest request) {
        if (request.getPurpose().isStorePurpose()) {
            Store store = getOwnedStore(ownerId);
            return new MediaTarget(MediaTargetType.STORE, store.getId());
        }
        if (request.getPurpose() == MediaPurpose.USER_AVATAR) {
            return new MediaTarget(MediaTargetType.USER, ownerId);
        }
        return new MediaTarget(request.getTargetType(), request.getTargetId());
    }

    private void applyConfirmedUpload(UUID ownerId, MediaUpload upload) {
        if (upload.getPurpose().isStorePurpose()) {
            Store store = getOwnedStore(ownerId);
            switch (upload.getPurpose()) {
                case STORE_COVER -> {
                    store.setCoverImageUrl(upload.getPublicUrl());
                    store.setCoverImageKey(upload.getObjectKey());
                }
                case STORE_LOGO -> {
                    store.setLogoUrl(upload.getPublicUrl());
                    store.setLogoKey(upload.getObjectKey());
                }
                case BUSINESS_LICENSE -> {
                    store.setBusinessLicenseImageUrl(upload.getPublicUrl());
                    store.setBusinessLicenseImageKey(upload.getObjectKey());
                }
                default -> throw new ApiException(ErrorCode.INVALID_INPUT, "Purpose không hợp lệ cho cửa hàng");
            }
            storeRepository.save(store);
            return;
        }

        if (upload.getPurpose() == MediaPurpose.USER_AVATAR) {
            User user = getUser(ownerId);
            user.setAvatarUrl(upload.getPublicUrl());
            userRepository.save(user);
        }
    }

    private String objectKey(UUID ownerId, MediaPurpose purpose, String contentType) {
        return ownerPrefix(ownerId, purpose) + UUID.randomUUID() + "." + extension(contentType);
    }

    private String ownerPrefix(UUID ownerId, MediaPurpose purpose) {
        return "uploads/" + ownerId + "/" + purpose.name().toLowerCase(Locale.ROOT) + "/";
    }

    private String extension(String contentType) {
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        return extension;
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private Store getOwnedStore(UUID ownerId) {
        return storeRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND, "Bạn chưa có cửa hàng"));
    }

    private MediaUploadResponse toResponse(MediaUpload upload) {
        return MediaUploadResponse.builder()
                .uploadId(upload.getId())
                .purpose(upload.getPurpose())
                .mediaType(upload.getMediaType())
                .targetType(upload.getTargetType())
                .targetId(upload.getTargetId())
                .key(upload.getObjectKey())
                .publicUrl(upload.getPublicUrl())
                .contentType(upload.getContentType())
                .fileSize(upload.getFileSize())
                .status(upload.getStatus())
                .confirmedAt(upload.getConfirmedAt())
                .build();
    }

    private record MediaTarget(MediaTargetType targetType, UUID targetId) {
    }
}
