package com.LastBite.modules.review.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "store_rating_summaries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreRatingSummary {

    @Id
    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "review_count", nullable = false)
    @Builder.Default
    private int reviewCount = 0;

    @Column(name = "recent_review_count", nullable = false)
    @Builder.Default
    private int recentReviewCount = 0;

    @Column(name = "overall_rating_avg", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal overallRatingAvg = BigDecimal.ZERO;

    @Column(name = "collection_rating_avg", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal collectionRatingAvg = BigDecimal.ZERO;

    @Column(name = "quality_rating_avg", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal qualityRatingAvg = BigDecimal.ZERO;

    @Column(name = "variety_rating_avg", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal varietyRatingAvg = BigDecimal.ZERO;

    @Column(name = "quantity_rating_avg", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal quantityRatingAvg = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
