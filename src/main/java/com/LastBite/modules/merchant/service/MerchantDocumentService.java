package com.LastBite.modules.merchant.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.merchant.dto.response.MerchantDocumentResponse;
import com.LastBite.modules.merchant.entity.MerchantDocument;
import com.LastBite.modules.merchant.enums.ReviewStatus;
import com.LastBite.modules.merchant.repository.MerchantBusinessProfileRepository;
import com.LastBite.modules.merchant.repository.MerchantDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantDocumentService {
    private final MerchantBusinessProfileRepository profileRepository;
    private final MerchantDocumentRepository documentRepository;

    @Transactional(readOnly = true)
    public List<MerchantDocumentResponse> list(UUID ownerId) {
        ensureProfileExists(ownerId);
        return documentRepository.findAllByBusinessProfileOwnerIdOrderByCreatedAtAsc(ownerId)
                .stream()
                .map(this::response)
                .toList();
    }

    @Transactional
    public void delete(UUID ownerId, UUID documentId) {
        MerchantDocument document = documentRepository.findByIdAndBusinessProfileOwnerId(documentId, ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay tai lieu"));
        if (document.getReviewStatus() == ReviewStatus.APPROVED) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Tai lieu da duyet khong the xoa truc tiep; hay upload ban thay the");
        }
        documentRepository.delete(document);
    }

    private void ensureProfileExists(UUID ownerId) {
        if (!profileRepository.existsByOwnerId(ownerId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Chua tao Business Profile");
        }
    }

    private MerchantDocumentResponse response(MerchantDocument document) {
        var upload = document.getMediaUpload();
        return MerchantDocumentResponse.builder()
                .id(document.getId())
                .businessProfileId(document.getBusinessProfile().getId())
                .uploadId(upload.getId())
                .documentType(document.getDocumentType())
                .reviewStatus(document.getReviewStatus())
                .expiresAt(document.getExpiresAt())
                .rejectionReason(document.getRejectionReason())
                .contentType(upload.getContentType())
                .fileSize(upload.getFileSize())
                .confirmedAt(upload.getConfirmedAt())
                .createdAt(document.getCreatedAt())
                .build();
    }
}
