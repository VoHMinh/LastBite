package com.LastBite.modules.review.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReviewResponse {
    private UUID id;
    private UUID orderId;
    private UUID userId;
    private UUID storeId;
    private UUID bagId;
    private int overallRating;
    private int collectionRating;
    private int qualityRating;
    private int varietyRating;
    private int quantityRating;
    private String comment;
    private boolean visible;
    private String hiddenReason;
    private List<String> photoUrls;
    private Instant createdAt;
    private Instant updatedAt;
}
