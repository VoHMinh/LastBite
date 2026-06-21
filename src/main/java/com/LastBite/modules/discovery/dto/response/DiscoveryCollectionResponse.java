package com.LastBite.modules.discovery.dto.response;

import com.LastBite.modules.discovery.enums.DiscoveryCollectionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveryCollectionResponse {
    private UUID id;
    private String slug;
    private String title;
    private DiscoveryCollectionType type;
    private Map<String, Object> ruleDefinition;
    private int displayOrder;
    private int maxItems;
    private int minItemsToDisplay;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
