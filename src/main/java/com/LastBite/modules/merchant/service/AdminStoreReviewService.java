package com.LastBite.modules.merchant.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.merchant.dto.response.AdminDocumentResponse;
import com.LastBite.modules.merchant.dto.response.AdminStoreReviewResponse;
import com.LastBite.modules.merchant.repository.MerchantDocumentRepository;
import com.LastBite.modules.merchant.repository.StoreReviewApplicationRepository;
import com.LastBite.modules.store.repository.StoreRepository;
import com.LastBite.modules.store.service.impl.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminStoreReviewService {
    private final StoreRepository storeRepository;
    private final StoreService storeService;
    private final MerchantBusinessProfileService businessProfileService;
    private final MerchantBankAccountService bankAccountService;
    private final MerchantDocumentRepository documentRepository;
    private final StoreReviewApplicationRepository applicationRepository;

    @Transactional(readOnly = true)
    public AdminStoreReviewResponse detail(UUID storeId) {
        var store = storeRepository.findDetailById(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));
        var profile = store.getBusinessProfile();
        var ownerId = profile.getOwner().getId();
        var documents = documentRepository.findAllByBusinessProfileIdOrderByCreatedAtAsc(profile.getId())
                .stream()
                .map(document -> AdminDocumentResponse.builder()
                        .id(document.getId())
                        .uploadId(document.getMediaUpload().getId())
                        .documentType(document.getDocumentType())
                        .reviewStatus(document.getReviewStatus())
                        .expiresAt(document.getExpiresAt())
                        .rejectionReason(document.getRejectionReason())
                        .build())
                .toList();
        var application = applicationRepository.findTopByStoreIdOrderByCreatedAtDesc(storeId).orElse(null);
        return AdminStoreReviewResponse.builder()
                .store(storeService.toDetailResponse(store))
                .businessProfile(businessProfileService.get(ownerId))
                .bankAccounts(bankAccountService.list(ownerId))
                .documents(documents)
                .pendingBusinessProfileSnapshot(application == null
                        || application.getBusinessProfileVersion() == null
                        ? null : application.getBusinessProfileVersion().getSnapshotJson())
                .pendingStoreSnapshot(application == null || application.getStoreVersion() == null
                        ? null : application.getStoreVersion().getSnapshotJson())
                .build();
    }
}
