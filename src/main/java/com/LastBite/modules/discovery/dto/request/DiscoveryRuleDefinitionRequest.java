package com.LastBite.modules.discovery.dto.request;

import com.LastBite.modules.discovery.enums.DiscoveryRule;
import com.LastBite.modules.discovery.enums.DiscoverySort;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class DiscoveryRuleDefinitionRequest {

    @NotNull(message = "Rule is required")
    private DiscoveryRule rule;

    @NotNull(message = "Sort is required")
    private DiscoverySort sort;

    private Map<String, Object> params = new LinkedHashMap<>();
}
