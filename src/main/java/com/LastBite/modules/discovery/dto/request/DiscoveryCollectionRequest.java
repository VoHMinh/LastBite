package com.LastBite.modules.discovery.dto.request;

import com.LastBite.modules.discovery.enums.DiscoveryCollectionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DiscoveryCollectionRequest {

    @NotBlank(message = "Slug is required")
    @Pattern(regexp = "^[a-z0-9_\\-]{3,50}$", message = "Slug must be 3-50 lowercase letters, numbers, underscore or dash")
    private String slug;

    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message = "Type is required")
    private DiscoveryCollectionType type;

    @Valid
    private DiscoveryRuleDefinitionRequest ruleDefinition;

    @Min(value = 0, message = "Display order must be non-negative")
    private int displayOrder = 0;

    @Min(value = 1, message = "Max items must be at least 1")
    @Max(value = 50, message = "Max items must be at most 50")
    private int maxItems = 10;

    @Min(value = 0, message = "Min items must be non-negative")
    @Max(value = 50, message = "Min items must be at most 50")
    private int minItemsToDisplay = 3;

    private Boolean active = true;
}
