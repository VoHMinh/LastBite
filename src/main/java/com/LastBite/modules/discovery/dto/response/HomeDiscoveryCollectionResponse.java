package com.LastBite.modules.discovery.dto.response;

import com.LastBite.modules.bag.dto.response.PublicBagSummaryResponse;
import com.LastBite.modules.discovery.enums.DiscoveryCollectionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeDiscoveryCollectionResponse implements Serializable {
    private UUID id;
    private String slug;
    private String title;
    private DiscoveryCollectionType type;
    private int displayOrder;
    private List<PublicBagSummaryResponse> items;
}
