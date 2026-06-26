package com.LastBite.modules.media.service.impl;

import com.LastBite.common.config.AwsS3Properties;
import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.media.dto.request.*;
import com.LastBite.modules.media.dto.response.*;
import com.LastBite.modules.media.entity.MediaUpload;
import com.LastBite.modules.media.enums.*;
import com.LastBite.modules.media.repository.MediaUploadRepository;
import com.LastBite.modules.media.service.*;
import com.LastBite.modules.merchant.enums.StoreMemberStatus;
import com.LastBite.modules.merchant.enums.ReviewStatus;
import com.LastBite.modules.merchant.entity.MerchantDocument;
import com.LastBite.modules.merchant.repository.*;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaUploadService implements MediaUploadServicePort {
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/webm", "video/quicktime");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp",
            "video/mp4", "mp4", "video/webm", "webm", "video/quicktime", "mov");

    private final MediaUploadRepository mediaUploadRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final MerchantBusinessProfileRepository profileRepository;
    private final MerchantStoreMemberRepository memberRepository;
    private final MerchantDocumentRepository documentRepository;
    private final MediaStorageServicePort storageService;
    private final MediaUrlService mediaUrlService;
    private final AwsS3Properties s3Properties;
    private final Clock clock;

    @Override
    @Transactional
    public PresignedUploadResponse createPresignedUploadUrl(UUID requesterId, CreatePresignedUploadRequest request) {
        User requester = getUser(requesterId);
        String contentType = normalize(request.getContentType());
        validateMedia(request.getPurpose(), contentType, request.getFileSize());
        MediaTarget target = resolveTarget(requester, request);
        validateGalleryLimit(request.getPurpose(), target);

        String key = objectKey(requesterId, target, request.getPurpose(), contentType);
        String publicUrl = request.getPurpose().isPrivateObject() ? null : s3Properties.publicUrl(key);
        MediaUpload upload = mediaUploadRepository.save(MediaUpload.builder()
                .owner(requester)
                .purpose(request.getPurpose())
                .mediaType(request.getPurpose().mediaType())
                .targetType(target.type())
                .targetId(target.id())
                .bucket(s3Properties.bucketName())
                .objectKey(key)
                .publicUrl(publicUrl)
                .privateObject(request.getPurpose().isPrivateObject())
                .contentType(contentType)
                .fileSize(request.getFileSize())
                .status(MediaUploadStatus.PENDING)
                .build());

        return PresignedUploadResponse.builder()
                .uploadId(upload.getId())
                .uploadUrl(storageService.createPresignedPutUrl(
                        upload.getBucket(), key, contentType,
                        Duration.ofSeconds(s3Properties.uploadExpireSeconds())))
                .key(key)
                .publicUrl(upload.isPrivateObject() ? null : mediaUrlService.signedUrlForKey(key))
                .expiresInSeconds(s3Properties.uploadExpireSeconds())
                .build();
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = {"store-detail", "store-by-slug", "store-list"}, allEntries = true),
            @CacheEvict(value = "user-profile", key = "#requesterId")
    })
    public MediaUploadResponse confirmUpload(UUID requesterId, ConfirmMediaUploadRequest request) {
        MediaUpload upload = mediaUploadRepository.findByIdAndOwnerId(request.getUploadId(), requesterId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy upload"));
        if (!upload.getObjectKey().equals(request.getKey().trim())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Object key không thuộc upload");
        }
        if (upload.getStatus() == MediaUploadStatus.CONFIRMED) return response(upload);
        if (!storageService.objectExists(upload.getBucket(), upload.getObjectKey())) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Object chưa tồn tại trên S3");
        }
        upload.setStatus(MediaUploadStatus.CONFIRMED);
        upload.setConfirmedAt(Instant.now(clock));
        upload = mediaUploadRepository.save(upload);
        applyConfirmedUpload(requesterId, upload);
        return response(upload);
    }

    @Override
    @Transactional(readOnly = true)
    public MediaAccessUrlResponse createPrivateAccessUrl(UUID requesterId, UUID uploadId) {
        MediaUpload upload = mediaUploadRepository.findById(uploadId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!upload.isPrivateObject() || upload.getStatus() != MediaUploadStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Tài liệu private chưa sẵn sàng");
        }
        User requester = getUser(requesterId);
        if (!requester.hasRole(UserRole.ADMIN) && !upload.getOwner().getId().equals(requesterId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        long ttl = Math.min(s3Properties.uploadExpireSeconds(), 300);
        return MediaAccessUrlResponse.builder()
                .accessUrl(storageService.createPresignedGetUrl(
                        upload.getBucket(), upload.getObjectKey(), Duration.ofSeconds(ttl)))
                .expiresInSeconds(ttl)
                .build();
    }

    private MediaTarget resolveTarget(User requester, CreatePresignedUploadRequest request) {
        MediaPurpose purpose = request.getPurpose();
        if (purpose.isStorePurpose()) {
            requireTarget(request, MediaTargetType.STORE);
            Store store = accessibleStore(requester, request.getTargetId());
            return new MediaTarget(MediaTargetType.STORE, store.getId());
        }
        if (purpose.isBusinessDocument()) {
            requireTarget(request, MediaTargetType.BUSINESS_PROFILE);
            if (!requester.hasRole(UserRole.MERCHANT_OWNER)) {
                throw new ApiException(ErrorCode.FORBIDDEN);
            }
            var profile = profileRepository.findById(request.getTargetId())
                    .filter(item -> item.getOwner().getId().equals(requester.getId()))
                    .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN));
            return new MediaTarget(MediaTargetType.BUSINESS_PROFILE, profile.getId());
        }
        if (purpose == MediaPurpose.USER_AVATAR) {
            return new MediaTarget(MediaTargetType.USER, requester.getId());
        }
        if (request.getTargetType() == null || request.getTargetId() == null) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD, "Thiếu targetType hoặc targetId");
        }
        return new MediaTarget(request.getTargetType(), request.getTargetId());
    }

    private Store accessibleStore(User requester, UUID storeId) {
        Store store = storeRepository.findDetailById(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));
        if (store.getBusinessProfile().getOwner().getId().equals(requester.getId())) return store;
        var membership = memberRepository
                .findByUserIdAndStoreIdAndStatus(requester.getId(), storeId, StoreMemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN));
        if (membership.getRole().getCode() != UserRole.MANAGER) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return store;
    }

    private void applyConfirmedUpload(UUID requesterId, MediaUpload upload) {
        if (upload.getPurpose().isStorePurpose()) {
            Store store = accessibleStore(getUser(requesterId), upload.getTargetId());
            switch (upload.getPurpose()) {
                case STORE_COVER -> {
                    store.setCoverImageUrl(upload.getPublicUrl());
                    store.setCoverImageKey(upload.getObjectKey());
                }
                case STORE_LOGO -> {
                    store.setLogoUrl(upload.getPublicUrl());
                    store.setLogoKey(upload.getObjectKey());
                }
                case STORE_STOREFRONT -> {
                    store.setStorefrontImageUrl(upload.getPublicUrl());
                    store.setStorefrontImageKey(upload.getObjectKey());
                }
                case STORE_MENU -> {
                    store.setMenuImageUrl(upload.getPublicUrl());
                    store.setMenuImageKey(upload.getObjectKey());
                }
                case STORE_GALLERY -> { }
                default -> throw new ApiException(ErrorCode.INVALID_INPUT);
            }
            storeRepository.save(store);
        } else if (upload.getPurpose() == MediaPurpose.USER_AVATAR) {
            User user = getUser(requesterId);
            user.setAvatarUrl(upload.getPublicUrl());
            userRepository.save(user);
        } else if (upload.getPurpose().isBusinessDocument()
                && !documentRepository.existsByMediaUploadId(upload.getId())) {
            var profile = profileRepository.findById(upload.getTargetId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
            documentRepository.save(MerchantDocument.builder()
                    .businessProfile(profile)
                    .mediaUpload(upload)
                    .documentType(upload.getPurpose().name())
                    .reviewStatus(ReviewStatus.PENDING_REVIEW)
                    .build());
        }
    }

    private void validateMedia(MediaPurpose purpose, String contentType, long size) {
        if (purpose.mediaType() == MediaType.IMAGE && !IMAGE_TYPES.contains(contentType)) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        if (purpose.mediaType() == MediaType.VIDEO && !VIDEO_TYPES.contains(contentType)) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        long max = purpose.mediaType() == MediaType.IMAGE
                ? s3Properties.maxImageSizeBytes() : s3Properties.maxVideoSizeBytes();
        if (size > max) throw new ApiException(ErrorCode.INVALID_INPUT, "File vượt quá dung lượng cho phép");
    }

    private void validateGalleryLimit(MediaPurpose purpose, MediaTarget target) {
        if (purpose == MediaPurpose.STORE_GALLERY
                && mediaUploadRepository.countByTargetTypeAndTargetIdAndPurpose(
                target.type(), target.id(), purpose) >= 5) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Tối đa 5 ảnh món ăn mẫu");
        }
    }

    private void requireTarget(CreatePresignedUploadRequest request, MediaTargetType type) {
        if (request.getTargetType() != type || request.getTargetId() == null) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD,
                    "Upload cần targetType " + type + " và targetId");
        }
    }

    private String objectKey(UUID userId, MediaTarget target, MediaPurpose purpose, String contentType) {
        String visibility = purpose.isPrivateObject() ? "private" : "public";
        return visibility + "/" + target.type().name().toLowerCase(Locale.ROOT) + "/"
                + target.id() + "/" + userId + "/" + purpose.name().toLowerCase(Locale.ROOT)
                + "/" + UUID.randomUUID() + "." + EXTENSIONS.get(contentType);
    }

    private User getUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private MediaUploadResponse response(MediaUpload upload) {
        return MediaUploadResponse.builder()
                .uploadId(upload.getId())
                .purpose(upload.getPurpose())
                .mediaType(upload.getMediaType())
                .targetType(upload.getTargetType())
                .targetId(upload.getTargetId())
                .key(upload.getObjectKey())
                .publicUrl(upload.isPrivateObject()
                        ? null
                        : mediaUrlService.resolveUrl(upload.getObjectKey(), upload.getPublicUrl()))
                .contentType(upload.getContentType())
                .fileSize(upload.getFileSize())
                .status(upload.getStatus())
                .confirmedAt(upload.getConfirmedAt())
                .build();
    }

    private record MediaTarget(MediaTargetType type, UUID id) {}
}
