package com.LastBite.modules.merchant.repository;

import com.LastBite.modules.merchant.entity.MerchantDocument;
import com.LastBite.modules.merchant.enums.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantDocumentRepository extends JpaRepository<MerchantDocument, UUID> {
    boolean existsByMediaUploadId(UUID mediaUploadId);
    boolean existsByBusinessProfileIdAndDocumentType(UUID businessProfileId, String documentType);
    List<MerchantDocument> findAllByBusinessProfileIdOrderByCreatedAtAsc(UUID businessProfileId);
    List<MerchantDocument> findAllByBusinessProfileOwnerIdOrderByCreatedAtAsc(UUID ownerId);
    Optional<MerchantDocument> findByIdAndBusinessProfileOwnerId(UUID id, UUID ownerId);
    List<MerchantDocument> findAllByBusinessProfileIdAndDocumentTypeAndReviewStatusNot(
            UUID businessProfileId, String documentType, ReviewStatus reviewStatus);
}
