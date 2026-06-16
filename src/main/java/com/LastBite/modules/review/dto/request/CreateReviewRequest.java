package com.LastBite.modules.review.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CreateReviewRequest {

    @Min(1)
    @Max(5)
    private int overallRating;

    @Min(1)
    @Max(5)
    private int collectionRating;

    @Min(1)
    @Max(5)
    private int qualityRating;

    @Min(1)
    @Max(5)
    private int varietyRating;

    @Min(1)
    @Max(5)
    private int quantityRating;

    @Size(max = 2000)
    private String comment;

    @Size(max = 5)
    private List<UUID> photoIds = List.of();
}
