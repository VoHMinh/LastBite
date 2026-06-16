package com.LastBite.modules.review.dto.response;

import com.LastBite.modules.review.enums.ReviewReportStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ReviewReportResponse {
    private UUID id;
    private UUID reviewId;
    private UUID reportedByUserId;
    private String reason;
    private ReviewReportStatus status;
    private String resolutionNote;
    private UUID resolvedByUserId;
    private Instant resolvedAt;
    private Instant createdAt;
}
