package com.LastBite.modules.analytics.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record TrackEngagementEventResponse(
        UUID eventId,
        Instant occurredAt
) {
}
