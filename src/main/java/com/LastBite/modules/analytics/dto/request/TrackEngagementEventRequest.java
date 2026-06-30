package com.LastBite.modules.analytics.dto.request;

import com.LastBite.modules.analytics.enums.EngagementEventType;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class TrackEngagementEventRequest {
    private EngagementEventType eventType;
    private UUID storeId;
    private UUID bagId;

    @Size(max = 80, message = "Source toi da 80 ky tu")
    private String source;

    @Size(max = 120, message = "Session id toi da 120 ky tu")
    private String sessionId;
}
