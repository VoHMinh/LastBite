package com.LastBite.modules.review.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class StoreRatingSummaryResponse {
    private UUID storeId;
    private int reviewCount;
    private int recentReviewCount;
    private BigDecimal overallRatingAvg;
    private BigDecimal collectionRatingAvg;
    private BigDecimal qualityRatingAvg;
    private BigDecimal varietyRatingAvg;
    private BigDecimal quantityRatingAvg;
    private Instant updatedAt;
}
