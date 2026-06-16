package com.LastBite.modules.review.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportReviewRequest {

    @NotBlank
    @Size(max = 50)
    private String reason;

    @Size(max = 1000)
    private String note;
}
