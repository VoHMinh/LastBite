package com.LastBite.modules.review.dto.request;

import com.LastBite.modules.review.enums.ReviewReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResolveReviewReportRequest {

    @NotNull
    private ReviewReportStatus status;

    @Size(max = 1000)
    private String resolutionNote;

    private Boolean hideReview;
}
