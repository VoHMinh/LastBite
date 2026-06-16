package com.LastBite.modules.merchant.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.merchant.dto.request.BusinessProfileRequest;
import com.LastBite.modules.merchant.dto.response.BusinessProfileResponse;
import com.LastBite.modules.merchant.entity.MerchantBusinessProfile;
import com.LastBite.modules.merchant.entity.MerchantBusinessProfileVersion;
import com.LastBite.modules.merchant.enums.BusinessLegalType;
import com.LastBite.modules.merchant.enums.ReviewStatus;
import com.LastBite.modules.merchant.repository.MerchantBusinessProfileRepository;
import com.LastBite.modules.merchant.repository.MerchantBusinessProfileVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MerchantBusinessProfileService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final MerchantBusinessProfileRepository profileRepository;
    private final MerchantBusinessProfileVersionRepository versionRepository;
    private final UserRepository userRepository;

    @Transactional
    public BusinessProfileResponse create(UUID ownerId, BusinessProfileRequest request) {
        if (profileRepository.existsByOwnerId(ownerId)) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Tài khoản đã có Business Profile");
        }
        var owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (!owner.hasRole(UserRole.MERCHANT_OWNER)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        validateConditionalFields(request);
        MerchantBusinessProfile profile = MerchantBusinessProfile.builder()
                .owner(owner)
                .reviewStatus(ReviewStatus.DRAFT)
                .build();
        apply(profile, request);
        return toResponse(profileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public BusinessProfileResponse get(UUID ownerId) {
        return toResponse(getEntity(ownerId));
    }

    @Transactional
    public BusinessProfileResponse update(UUID ownerId, BusinessProfileRequest request) {
        MerchantBusinessProfile profile = getEntity(ownerId);
        validateConditionalFields(request);
        if (profile.getReviewStatus() == ReviewStatus.APPROVED) {
            MerchantBusinessProfileVersion version = MerchantBusinessProfileVersion.builder()
                    .businessProfile(profile)
                    .versionNumber(versionRepository.maxVersionNumber(profile.getId()) + 1)
                    .snapshotJson(writeSnapshot(request))
                    .reviewStatus(ReviewStatus.PENDING_REVIEW)
                    .submittedAt(Instant.now())
                    .build();
            versionRepository.save(version);
            return toResponse(profile);
        }
        apply(profile, request);
        profile.setReviewStatus(ReviewStatus.DRAFT);
        profile.setRejectionReason(null);
        return toResponse(profileRepository.save(profile));
    }

    public MerchantBusinessProfile getEntity(UUID ownerId) {
        return profileRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Chưa tạo Business Profile"));
    }

    @Transactional
    public void approveVersion(MerchantBusinessProfileVersion version) {
        if (version == null || version.getReviewStatus() != ReviewStatus.PENDING_REVIEW) return;
        try {
            BusinessProfileRequest request = OBJECT_MAPPER.readValue(
                    version.getSnapshotJson(), BusinessProfileRequest.class);
            MerchantBusinessProfile profile = version.getBusinessProfile();
            if (isBlank(request.getIdentityDocumentNumber())) {
                request.setIdentityDocumentNumber(profile.getIdentityDocumentNumber());
            }
            apply(profile, request);
            profile.setReviewStatus(ReviewStatus.APPROVED);
            profile.setApprovedAt(Instant.now());
            profile.setRejectionReason(null);
            profileRepository.save(profile);
            version.setReviewStatus(ReviewStatus.APPROVED);
            version.setReviewedAt(Instant.now());
            versionRepository.save(version);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.UNEXPECTED_ERROR, "Không thể áp dụng phiên bản Business Profile");
        }
    }

    private void validateConditionalFields(BusinessProfileRequest request) {
        BusinessLegalType type = request.getLegalType();
        if (type != BusinessLegalType.INDIVIDUAL
                && isBlank(request.getRegistrationNumber())) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD,
                    "Số đăng ký kinh doanh là bắt buộc với loại hình đã chọn");
        }
        if ((type == BusinessLegalType.COMPANY || type == BusinessLegalType.COMPANY_BRANCH)
                && isBlank(request.getTaxCode())) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD, "Mã số thuế là bắt buộc");
        }
        if (type == BusinessLegalType.COMPANY_BRANCH
                && (isBlank(request.getParentCompanyName()) || isBlank(request.getParentCompanyTaxCode()))) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD,
                    "Chi nhánh phải khai báo thông tin công ty mẹ");
        }
    }

    private void apply(MerchantBusinessProfile profile, BusinessProfileRequest request) {
        profile.setLegalType(request.getLegalType());
        profile.setLegalName(trim(request.getLegalName()));
        profile.setRepresentativeFullName(request.getRepresentativeFullName().trim());
        profile.setRepresentativePhone(trim(request.getRepresentativePhone()));
        profile.setRepresentativeEmail(trim(request.getRepresentativeEmail()));
        profile.setIdentityDocumentType(trim(request.getIdentityDocumentType()));
        profile.setIdentityDocumentNumber(trim(request.getIdentityDocumentNumber()));
        profile.setTaxCode(trim(request.getTaxCode()));
        profile.setRegistrationNumber(trim(request.getRegistrationNumber()));
        profile.setParentCompanyName(trim(request.getParentCompanyName()));
        profile.setParentCompanyTaxCode(trim(request.getParentCompanyTaxCode()));
        profile.setBusinessAddress(trim(request.getBusinessAddress()));
    }

    private BusinessProfileResponse toResponse(MerchantBusinessProfile profile) {
        return BusinessProfileResponse.builder()
                .id(profile.getId())
                .ownerUserId(profile.getOwner().getId())
                .legalType(profile.getLegalType())
                .legalName(profile.getLegalName())
                .representativeFullName(profile.getRepresentativeFullName())
                .representativePhone(profile.getRepresentativePhone())
                .representativeEmail(profile.getRepresentativeEmail())
                .identityDocumentType(profile.getIdentityDocumentType())
                .maskedIdentityDocumentNumber(mask(profile.getIdentityDocumentNumber()))
                .taxCode(profile.getTaxCode())
                .registrationNumber(profile.getRegistrationNumber())
                .parentCompanyName(profile.getParentCompanyName())
                .parentCompanyTaxCode(profile.getParentCompanyTaxCode())
                .businessAddress(profile.getBusinessAddress())
                .reviewStatus(profile.getReviewStatus())
                .rejectionReason(profile.getRejectionReason())
                .approvedAt(profile.getApprovedAt())
                .build();
    }

    private String writeSnapshot(BusinessProfileRequest request) {
        try {
            return OBJECT_MAPPER.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.UNEXPECTED_ERROR, "Không thể tạo phiên bản hồ sơ");
        }
    }

    private String mask(String value) {
        if (value == null || value.length() <= 4) return value;
        return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }

    private String trim(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
