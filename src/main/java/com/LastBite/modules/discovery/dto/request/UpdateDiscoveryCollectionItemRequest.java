package com.LastBite.modules.discovery.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDiscoveryCollectionItemRequest {

    @Min(value = 0, message = "Pinned order must be non-negative")
    private Integer pinnedOrder;
}
