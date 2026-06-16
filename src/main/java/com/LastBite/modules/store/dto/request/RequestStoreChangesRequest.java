package com.LastBite.modules.store.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class RequestStoreChangesRequest {
    @NotEmpty
    @Valid
    private List<FeedbackItem> items;

    @Data
    public static class FeedbackItem {
        @NotBlank
        private String section;
        @NotBlank
        private String fieldPath;
        @NotBlank
        private String message;
    }
}
