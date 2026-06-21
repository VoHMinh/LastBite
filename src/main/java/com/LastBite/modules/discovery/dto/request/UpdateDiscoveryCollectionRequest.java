package com.LastBite.modules.discovery.dto.request;

import com.LastBite.modules.discovery.enums.DiscoveryCollectionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDiscoveryCollectionRequest {

    @Pattern(regexp = "^[a-z0-9_\\-]{3,50}$", message = "Slug must be 3-50 lowercase letters, numbers, underscore or dash")
    private String slug;

    private String title;

    private DiscoveryCollectionType type;

    @Valid
    private DiscoveryRuleDefinitionRequest ruleDefinition;

    @Min(value = 0, message = "Display order must be non-negative")
    private Integer displayOrder;

    @Min(value = 1, message = "Max items must be at least 1")
    @Max(value = 50, message = "Max items must be at most 50")
    private Integer maxItems;

    @Min(value = 0, message = "Min items must be non-negative")
    @Max(value = 50, message = "Min items must be at most 50")
    private Integer minItemsToDisplay;

    private Boolean active;
}
