package com.LastBite.modules.store.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.response.PageResponse;
import com.LastBite.common.util.SlugUtil;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.media.enums.*;
import com.LastBite.modules.media.repository.MediaUploadRepository;
import com.LastBite.modules.merchant.entity.MerchantBusinessProfile;
import com.LastBite.modules.merchant.entity.StoreReviewApplication;
import com.LastBite.modules.merchant.entity.ReviewFeedbackItem;
import com.LastBite.modules.merchant.entity.StoreVersion;
import com.LastBite.modules.merchant.enums.BusinessLegalType;
import com.LastBite.modules.merchant.enums.ReviewStatus;
import com.LastBite.modules.merchant.repository.MerchantBusinessProfileRepository;
import com.LastBite.modules.merchant.repository.MerchantBankAccountRepository;
import com.LastBite.modules.merchant.repository.MerchantDocumentRepository;
import com.LastBite.modules.merchant.repository.StoreReviewApplicationRepository;
import com.LastBite.modules.merchant.repository.ReviewFeedbackItemRepository;
import com.LastBite.modules.merchant.repository.StoreVersionRepository;
import com.LastBite.modules.merchant.repository.MerchantBusinessProfileVersionRepository;
import com.LastBite.modules.merchant.service.MerchantBusinessProfileService;
import com.LastBite.modules.notification.service.NotificationServicePort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.LastBite.modules.store.dto.request.*;
import com.LastBite.modules.store.dto.response.StoreDetailResponse;
import com.LastBite.modules.store.entity.*;
import com.LastBite.modules.store.enums.*;
import com.LastBite.modules.store.repository.*;
import com.LastBite.modules.store.service.StoreServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreService implements StoreServicePort {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final MerchantBusinessProfileRepository profileRepository;
    private final StoreReviewApplicationRepository reviewApplicationRepository;
    private final ReviewFeedbackItemRepository feedbackItemRepository;
    private final StoreReliabilityStatsRepository reliabilityStatsRepository;
    private final MediaUploadRepository mediaUploadRepository;
    private final MerchantBankAccountRepository bankAccountRepository;
    private final MerchantDocumentRepository documentRepository;
    private final StoreVersionRepository storeVersionRepository;
    private final MerchantBusinessProfileVersionRepository businessProfileVersionRepository;
    private final MerchantBusinessProfileService businessProfileService;
    private final NotificationServicePort notificationService;

    @Transactional
    public Store createStoreInternal(User owner, CreateStoreRequest request) {
        MerchantBusinessProfile profile = profileRepository.findByOwnerId(owner.getId())
                .orElseGet(() -> profileRepository.save(MerchantBusinessProfile.builder()
                        .owner(owner)
                        .legalType(BusinessLegalType.INDIVIDUAL)
                        .representativeFullName(owner.getFullName())
                        .representativePhone(owner.getPhone())
                        .representativeEmail(owner.getEmail())
                        .reviewStatus(ReviewStatus.DRAFT)
                        .build()));
        return createStoreEntity(owner, profile, request);
    }

    @Transactional
    public StoreDetailResponse createStore(UUID ownerId, CreateStoreRequest request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        MerchantBusinessProfile profile = profileRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST,
                        "Cần tạo Business Profile trước khi tạo cửa hàng"));
        return toDetailResponse(createStoreEntity(owner, profile, request));
    }

    private Store createStoreEntity(User owner, MerchantBusinessProfile profile, CreateStoreRequest request) {
        String slug = SlugUtil.toUniqueSlug(request.getName(), storeRepository::existsBySlug);
        Store store = Store.builder()
                .createdBy(owner)
                .businessProfile(profile)
                .name(request.getName().trim())
                .slug(slug)
                .description(request.getDescription())
                .category(request.getCategory())
                .phone(request.getPhone() != null ? request.getPhone() : owner.getPhone())
                .email(request.getEmail() != null ? request.getEmail() : owner.getEmail())
                .address(request.getAddress().trim())
                .district(request.getDistrict())
                .city(request.getCity() != null ? request.getCity() : "ho-chi-minh")
                .lat(request.getLat())
                .lng(request.getLng())
                .pickupInstructions(request.getPickupInstructions())
                .coverImageUrl(request.getCoverImageUrl())
                .logoUrl(request.getLogoUrl())
                .businessLicenseNumber(request.getBusinessLicenseNumber())
                .businessLicenseImageUrl(request.getBusinessLicenseImageUrl())
                .status(StoreStatus.DRAFT)
                .verificationStatus(VerificationStatus.DRAFT)
                .build();
        store = storeRepository.save(store);
        reliabilityStatsRepository.save(StoreReliabilityStats.builder().store(store).build());
        return store;
    }

    @Transactional(readOnly = true)
    public List<StoreDetailResponse> listMyStores(UUID ownerId) {
        return storeRepository.findAllByBusinessProfileOwnerIdOrderByCreatedAtAsc(ownerId)
                .stream().map(this::toDetailResponse).toList();
    }

    public StoreDetailResponse getMyStore(UUID ownerId) {
        return toDetailResponse(firstOwnedStore(ownerId));
    }

    @Transactional(readOnly = true)
    public StoreDetailResponse getStore(UUID ownerId, UUID storeId) {
        return toDetailResponse(ownedStore(ownerId, storeId));
    }

    @Transactional
    @CacheEvict(value = {"store-detail", "store-by-slug", "store-list", "bag-discovery", "home-discovery", "store-bags"}, allEntries = true)
    public StoreDetailResponse updateStore(UUID ownerId, UpdateStoreRequest request) {
        return updateStore(ownerId, firstOwnedStore(ownerId).getId(), request);
    }

    @Transactional
    @CacheEvict(value = {"store-detail", "store-by-slug", "store-list", "bag-discovery", "home-discovery", "store-bags"}, allEntries = true)
    public StoreDetailResponse updateStore(UUID ownerId, UUID storeId, UpdateStoreRequest request) {
        Store store = ownedStore(ownerId, storeId);
        if (store.getVerificationStatus() == VerificationStatus.VERIFIED
                && hasReviewSensitiveChange(request)) {
            storeVersionRepository.save(StoreVersion.builder()
                    .store(store)
                    .versionNumber(storeVersionRepository.maxVersionNumber(storeId) + 1)
                    .snapshotJson(writeStoreSnapshot(request))
                    .reviewStatus(ReviewStatus.PENDING_REVIEW)
                    .submittedAt(Instant.now())
                    .build());
            applyImmediateStoreFields(store, request);
            return toDetailResponse(storeRepository.save(store));
        }
        applyStoreFields(store, request);
        if (store.getVerificationStatus() == VerificationStatus.REJECTED
                || store.getVerificationStatus() == VerificationStatus.CHANGES_REQUESTED) {
            store.setVerificationStatus(VerificationStatus.DRAFT);
        }
        return toDetailResponse(storeRepository.save(store));
    }

    private void applyStoreFields(Store store, UpdateStoreRequest request) {
        if (request.getName() != null && !request.getName().isBlank()) {
            store.setName(request.getName().trim());
            String currentSlug = store.getSlug();
            store.setSlug(SlugUtil.toUniqueSlug(request.getName(),
                    slug -> !slug.equals(currentSlug) && storeRepository.existsBySlug(slug)));
        }
        if (request.getDescription() != null) store.setDescription(request.getDescription());
        if (request.getCategory() != null) store.setCategory(request.getCategory());
        if (request.getPhone() != null) store.setPhone(request.getPhone());
        if (request.getEmail() != null) store.setEmail(request.getEmail());
        if (request.getAddress() != null) store.setAddress(request.getAddress().trim());
        if (request.getDistrict() != null) store.setDistrict(request.getDistrict());
        if (request.getCity() != null) store.setCity(request.getCity());
        if (request.getLat() != null) store.setLat(request.getLat());
        if (request.getLng() != null) store.setLng(request.getLng());
        if (request.getPickupInstructions() != null) store.setPickupInstructions(request.getPickupInstructions());
        if (request.getCoverImageUrl() != null) store.setCoverImageUrl(request.getCoverImageUrl());
        if (request.getLogoUrl() != null) store.setLogoUrl(request.getLogoUrl());
        if (request.getBusinessLicenseNumber() != null) store.setBusinessLicenseNumber(request.getBusinessLicenseNumber());
        if (request.getBusinessLicenseImageUrl() != null) store.setBusinessLicenseImageUrl(request.getBusinessLicenseImageUrl());
    }

    private void applyImmediateStoreFields(Store store, UpdateStoreRequest request) {
        if (request.getDescription() != null) store.setDescription(request.getDescription());
        if (request.getPhone() != null) store.setPhone(request.getPhone());
        if (request.getEmail() != null) store.setEmail(request.getEmail());
        if (request.getPickupInstructions() != null) store.setPickupInstructions(request.getPickupInstructions());
    }

    @Transactional
    public StoreDetailResponse updateSchedules(UUID ownerId, List<ScheduleRequest> requests) {
        return updateSchedules(ownerId, firstOwnedStore(ownerId).getId(), requests);
    }

    @Transactional
    public StoreDetailResponse updateSchedules(UUID ownerId, UUID storeId, List<ScheduleRequest> requests) {
        Store store = ownedStore(ownerId, storeId);
        List<StoreSchedule> schedules = requests.stream().map(request -> {
            StoreSchedule schedule = new StoreSchedule();
            schedule.setDayOfWeek(request.getDayOfWeek());
            schedule.setOpenTime(request.getOpenTime());
            schedule.setCloseTime(request.getCloseTime());
            schedule.setOpen(Boolean.TRUE.equals(request.getIsOpen()));
            return schedule;
        }).toList();
        store.replaceSchedules(schedules);
        return toDetailResponse(storeRepository.save(store));
    }

    @Transactional
    public StoreDetailResponse pauseStore(UUID ownerId) {
        return pauseStore(ownerId, firstOwnedStore(ownerId).getId());
    }

    @Transactional
    public StoreDetailResponse pauseStore(UUID ownerId, UUID storeId) {
        Store store = ownedStore(ownerId, storeId);
        store.setStatus(StoreStatus.PAUSED);
        return toDetailResponse(storeRepository.save(store));
    }

    @Transactional
    public StoreDetailResponse activateStore(UUID ownerId) {
        return activateStore(ownerId, firstOwnedStore(ownerId).getId());
    }

    @Transactional
    public StoreDetailResponse activateStore(UUID ownerId, UUID storeId) {
        Store store = ownedStore(ownerId, storeId);
        if (store.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Cửa hàng chưa được duyệt");
        }
        store.setStatus(StoreStatus.ACTIVE);
        return toDetailResponse(storeRepository.save(store));
    }

    @Transactional
    public StoreDetailResponse submitReview(UUID ownerId) {
        return submitReview(ownerId, firstOwnedStore(ownerId).getId());
    }

    @Transactional
    public StoreDetailResponse submitReview(UUID ownerId, UUID storeId) {
        Store store = ownedStore(ownerId, storeId);
        validateReviewReady(store);
        if (store.getVerificationStatus() != VerificationStatus.VERIFIED) {
            store.setVerificationStatus(VerificationStatus.PENDING);
            store.setRejectionReason(null);
        }
        MerchantBusinessProfile profile = store.getBusinessProfile();
        if (profile.getReviewStatus() != ReviewStatus.APPROVED) {
            profile.setReviewStatus(ReviewStatus.PENDING_REVIEW);
            profile.setRejectionReason(null);
            profileRepository.save(profile);
        }
        var businessVersion = businessProfileVersionRepository
                .findTopByBusinessProfileIdAndReviewStatusOrderByVersionNumberDesc(
                        profile.getId(), ReviewStatus.PENDING_REVIEW)
                .orElse(null);
        var storeVersion = storeVersionRepository
                .findTopByStoreIdAndReviewStatusOrderByVersionNumberDesc(
                        storeId, ReviewStatus.PENDING_REVIEW)
                .orElse(null);
        reviewApplicationRepository.save(StoreReviewApplication.builder()
                .store(store)
                .businessProfileVersion(businessVersion)
                .storeVersion(storeVersion)
                .status(ReviewStatus.PENDING_REVIEW)
                .submittedBy(userRepository.getReferenceById(ownerId))
                .submittedAt(Instant.now())
                .build());
        Store saved = storeRepository.save(store);
        notificationService.notifyAdminStorePendingReview(saved);
        return toDetailResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<StoreDetailResponse> listStoresForReview(VerificationStatus status, Pageable pageable) {
        var page = status == VerificationStatus.PENDING
                ? reviewApplicationRepository.findAllByStatus(ReviewStatus.PENDING_REVIEW, pageable)
                    .map(application -> toDetailResponse(application.getStore()))
                : storeRepository.findAllByVerificationStatus(status, pageable).map(this::toDetailResponse);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public StoreDetailResponse getStoreForReview(UUID storeId) {
        return toDetailResponse(getStoreDetail(storeId));
    }

    @Transactional
    @CacheEvict(value = {"store-detail", "store-by-slug", "store-list", "bag-discovery", "home-discovery", "store-bags"}, allEntries = true)
    public StoreDetailResponse approveStore(UUID storeId) {
        Store store = getStoreDetail(storeId);
        StoreReviewApplication latestApplication = reviewApplicationRepository
                .findTopByStoreIdOrderByCreatedAtDesc(storeId)
                .orElse(null);
        if (latestApplication != null) {
            businessProfileService.approveVersion(latestApplication.getBusinessProfileVersion());
            approveStoreVersion(latestApplication.getStoreVersion());
        }
        store.setVerificationStatus(VerificationStatus.VERIFIED);
        store.setStatus(StoreStatus.ACTIVE);
        store.setRejectionReason(null);
        MerchantBusinessProfile profile = store.getBusinessProfile();
        if (profile.getReviewStatus() != ReviewStatus.APPROVED) {
            profile.setReviewStatus(ReviewStatus.APPROVED);
            profile.setApprovedAt(Instant.now());
            profile.setRejectionReason(null);
            profileRepository.save(profile);
        }
        reviewApplicationRepository.findTopByStoreIdOrderByCreatedAtDesc(storeId).ifPresent(application -> {
            application.setStatus(ReviewStatus.APPROVED);
            application.setReviewedAt(Instant.now());
            reviewApplicationRepository.save(application);
        });
        Store saved = storeRepository.save(store);
        notificationService.notifyMerchantStoreApproved(saved);
        return toDetailResponse(saved);
    }

    @Transactional
    public StoreDetailResponse rejectStore(UUID storeId, String reason) {
        Store store = getStoreDetail(storeId);
        reviewApplicationRepository.findTopByStoreIdOrderByCreatedAtDesc(storeId).ifPresent(application -> {
            boolean revision = application.getBusinessProfileVersion() != null
                    || application.getStoreVersion() != null;
            rejectVersions(application, reason.trim(), ReviewStatus.REJECTED);
            application.setStatus(ReviewStatus.REJECTED);
            application.setDecisionNote(reason.trim());
            application.setReviewedAt(Instant.now());
            reviewApplicationRepository.save(application);
            if (!revision) {
                store.setVerificationStatus(VerificationStatus.REJECTED);
                store.setStatus(StoreStatus.DRAFT);
                store.setRejectionReason(reason.trim());
            }
        });
        Store saved = storeRepository.save(store);
        notificationService.notifyMerchantStoreRejected(saved, reason);
        return toDetailResponse(saved);
    }

    @Transactional
    public StoreDetailResponse requestChanges(UUID adminId, UUID storeId, RequestStoreChangesRequest request) {
        Store store = getStoreDetail(storeId);
        StoreReviewApplication application = reviewApplicationRepository
                .findTopByStoreIdOrderByCreatedAtDesc(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hồ sơ duyệt"));
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        request.getItems().forEach(item -> feedbackItemRepository.save(ReviewFeedbackItem.builder()
                .application(application)
                .section(item.getSection().trim())
                .fieldPath(item.getFieldPath().trim())
                .message(item.getMessage().trim())
                .createdBy(admin)
                .build()));
        application.setStatus(ReviewStatus.CHANGES_REQUESTED);
        application.setReviewedBy(admin);
        application.setReviewedAt(Instant.now());
        reviewApplicationRepository.save(application);
        boolean revision = application.getBusinessProfileVersion() != null
                || application.getStoreVersion() != null;
        rejectVersions(application, "Admin yêu cầu chỉnh sửa", ReviewStatus.CHANGES_REQUESTED);
        if (!revision) {
            store.setVerificationStatus(VerificationStatus.CHANGES_REQUESTED);
            store.setStatus(StoreStatus.DRAFT);
            store.setRejectionReason("Admin yêu cầu bổ sung hoặc chỉnh sửa hồ sơ");
        }
        if (!revision && store.getBusinessProfile().getReviewStatus() != ReviewStatus.APPROVED) {
            store.getBusinessProfile().setReviewStatus(ReviewStatus.CHANGES_REQUESTED);
            profileRepository.save(store.getBusinessProfile());
        }
        return toDetailResponse(storeRepository.save(store));
    }

    private boolean hasReviewSensitiveChange(UpdateStoreRequest request) {
        return request.getName() != null
                || request.getCategory() != null
                || request.getAddress() != null
                || request.getDistrict() != null
                || request.getCity() != null
                || request.getLat() != null
                || request.getLng() != null
                || request.getCoverImageUrl() != null
                || request.getLogoUrl() != null
                || request.getBusinessLicenseNumber() != null
                || request.getBusinessLicenseImageUrl() != null;
    }

    private String writeStoreSnapshot(UpdateStoreRequest request) {
        try {
            return OBJECT_MAPPER.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.UNEXPECTED_ERROR, "Không thể tạo phiên bản cửa hàng");
        }
    }

    private void approveStoreVersion(StoreVersion version) {
        if (version == null || version.getReviewStatus() != ReviewStatus.PENDING_REVIEW) return;
        try {
            UpdateStoreRequest request = OBJECT_MAPPER.readValue(
                    version.getSnapshotJson(), UpdateStoreRequest.class);
            applyStoreFields(version.getStore(), request);
            storeRepository.save(version.getStore());
            version.setReviewStatus(ReviewStatus.APPROVED);
            version.setReviewedAt(Instant.now());
            storeVersionRepository.save(version);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.UNEXPECTED_ERROR, "Không thể áp dụng phiên bản cửa hàng");
        }
    }

    private void rejectVersions(
            StoreReviewApplication application, String reason, ReviewStatus status) {
        if (application.getBusinessProfileVersion() != null) {
            var version = application.getBusinessProfileVersion();
            version.setReviewStatus(status);
            version.setRejectionReason(reason);
            version.setReviewedAt(Instant.now());
            businessProfileVersionRepository.save(version);
        }
        if (application.getStoreVersion() != null) {
            var version = application.getStoreVersion();
            version.setReviewStatus(status);
            version.setRejectionReason(reason);
            version.setReviewedAt(Instant.now());
            storeVersionRepository.save(version);
        }
    }

    private void validateReviewReady(Store store) {
        if (store.getSchedules().stream().noneMatch(StoreSchedule::isOpen)) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD,
                    "Cần cấu hình ít nhất một ngày mở cửa");
        }
        if (mediaUploadRepository.findAllByTargetTypeAndTargetIdAndPurposeAndStatusOrderByCreatedAtAsc(
                MediaTargetType.STORE, store.getId(), MediaPurpose.STORE_GALLERY,
                MediaUploadStatus.CONFIRMED).isEmpty()) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD,
                    "Cần ít nhất một ảnh món ăn mẫu");
        }

        MerchantBusinessProfile profile = store.getBusinessProfile();
        requireDocument(profile, MediaPurpose.REPRESENTATIVE_ID);
        requireDocument(profile, MediaPurpose.BANK_PROOF);
        if (profile.getLegalType() != BusinessLegalType.INDIVIDUAL) {
            requireDocument(profile, MediaPurpose.BUSINESS_LICENSE);
        }
        if (profile.getLegalType() == BusinessLegalType.COMPANY_BRANCH) {
            requireDocument(profile, MediaPurpose.AUTHORIZATION_LETTER);
        }
        if (!bankAccountRepository.existsByBusinessProfileId(profile.getId())) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD,
                    "Cần khai báo tài khoản nhận tiền");
        }
        if (store.getLat() == null || store.getLng() == null) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD, "Cần xác nhận vị trí pickup trên bản đồ");
        }
        if (store.getCoverImageUrl() == null || store.getLogoUrl() == null
                || store.getStorefrontImageUrl() == null || store.getMenuImageUrl() == null) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD,
                    "Thiếu ảnh bìa, logo, mặt tiền hoặc thực đơn");
        }
    }

    private void requireDocument(MerchantBusinessProfile profile, MediaPurpose purpose) {
        if (!documentRepository.existsByBusinessProfileIdAndDocumentType(
                profile.getId(), purpose.name())) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD,
                    "Thiếu tài liệu bắt buộc: " + purpose.name());
        }
    }

    private Store firstOwnedStore(UUID ownerId) {
        return storeRepository.findFirstByBusinessProfileOwnerIdOrderByCreatedAtAsc(ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND, "Bạn chưa có cửa hàng"));
    }

    private Store ownedStore(UUID ownerId, UUID storeId) {
        return storeRepository.findByIdAndBusinessProfileOwnerId(storeId, ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));
    }

    private Store getStoreDetail(UUID storeId) {
        return storeRepository.findDetailById(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));
    }

    public StoreDetailResponse toDetailResponse(Store store) {
        List<StoreDetailResponse.ScheduleResponse> schedules = store.getSchedules().stream()
                .map(item -> StoreDetailResponse.ScheduleResponse.builder()
                        .dayOfWeek(item.getDayOfWeek())
                        .openTime(item.getOpenTime())
                        .closeTime(item.getCloseTime())
                        .isOpen(item.isOpen())
                        .build())
                .toList();
        return StoreDetailResponse.builder()
                .id(store.getId())
                .ownerId(store.getBusinessProfile().getOwner().getId())
                .businessProfileId(store.getBusinessProfile().getId())
                .name(store.getName())
                .slug(store.getSlug())
                .description(store.getDescription())
                .category(store.getCategory())
                .phone(store.getPhone())
                .email(store.getEmail())
                .address(store.getAddress())
                .district(store.getDistrict())
                .city(store.getCity())
                .lat(store.getLat())
                .lng(store.getLng())
                .pickupInstructions(store.getPickupInstructions())
                .storefrontImageUrl(store.getStorefrontImageUrl())
                .menuImageUrl(store.getMenuImageUrl())
                .coverImageUrl(store.getCoverImageUrl())
                .logoUrl(store.getLogoUrl())
                .galleryImageUrls(galleryImageUrls(store.getId()))
                .businessLicenseNumber(store.getBusinessLicenseNumber())
                .businessLicenseImageUrl(store.getBusinessLicenseImageUrl())
                .status(store.getStatus())
                .verificationStatus(store.getVerificationStatus())
                .rejectionReason(store.getRejectionReason())
                .avgRating(store.getAvgRating())
                .totalRatings(store.getTotalRatings())
                .createdAt(store.getCreatedAt())
                .updatedAt(store.getUpdatedAt())
                .schedules(schedules)
                .build();
    }

    private List<String> galleryImageUrls(UUID storeId) {
        return mediaUploadRepository
                .findAllByTargetTypeAndTargetIdAndPurposeAndStatusOrderByCreatedAtAsc(
                        MediaTargetType.STORE, storeId, MediaPurpose.STORE_GALLERY, MediaUploadStatus.CONFIRMED)
                .stream().map(upload -> upload.getPublicUrl()).toList();
    }
}
