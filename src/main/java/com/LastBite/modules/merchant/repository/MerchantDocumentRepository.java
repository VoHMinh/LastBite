package com.LastBite.modules.merchant.repository;

import com.LastBite.modules.merchant.entity.MerchantDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MerchantDocumentRepository extends JpaRepository<MerchantDocument, UUID> {
    boolean existsByMediaUploadId(UUID mediaUploadId);
    boolean existsByBusinessProfileIdAndDocumentType(UUID businessProfileId, String documentType);
    List<MerchantDocument> findAllByBusinessProfileIdOrderByCreatedAtAsc(UUID businessProfileId);
}
