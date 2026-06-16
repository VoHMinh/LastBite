package com.LastBite.modules.merchant.dto.response;

import com.LastBite.modules.merchant.enums.ReviewStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class AdminDocumentResponse {
    private UUID id;
    private UUID uploadId;
    private String documentType;
    private ReviewStatus reviewStatus;
    private LocalDate expiresAt;
    private String rejectionReason;
}
