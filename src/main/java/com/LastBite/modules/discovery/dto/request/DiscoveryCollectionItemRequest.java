package com.LastBite.modules.discovery.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class DiscoveryCollectionItemRequest {

    @NotNull(message = "Bag id is required")
    private UUID bagId;

    @Min(value = 0, message = "Pinned order must be non-negative")
    private Integer pinnedOrder;
}
