package com.LastBite.modules.discovery.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveryCollectionItemResponse {
    private UUID id;
    private UUID collectionId;
    private UUID bagId;
    private String bagName;
    private Integer pinnedOrder;
    private Instant addedAt;
}
