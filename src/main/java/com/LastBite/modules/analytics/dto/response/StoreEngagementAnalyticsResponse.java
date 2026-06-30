package com.LastBite.modules.analytics.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record StoreEngagementAnalyticsResponse(
        UUID storeId,
        Instant from,
        Instant to,
        String timezone,
        Overview overview,
        List<HourlyBucket> hourly,
        List<DailyBucket> daily,
        List<SourceBucket> sources,
        List<TopBagBucket> topBags
) {
    @Builder
    public record Overview(
            long storeViews,
            long storeCardClicks,
            long bagViews,
            long bagCardClicks,
            long totalEngagements,
            long knownUsers,
            long anonymousSessions,
            long totalOrders,
            long paidOrders,
            long bagsSold,
            BigDecimal grossRevenue,
            double paidOrderConversionRate
    ) {
    }

    @Builder
    public record HourlyBucket(
            int hour,
            long storeViews,
            long bagViews,
            long cardClicks,
            long totalEvents
    ) {
    }

    @Builder
    public record DailyBucket(
            String date,
            long storeViews,
            long bagViews,
            long cardClicks,
            long totalEvents
    ) {
    }

    @Builder
    public record SourceBucket(
            String source,
            long totalEvents
    ) {
    }

    @Builder
    public record TopBagBucket(
            UUID bagId,
            String bagName,
            long views,
            long cardClicks,
            long totalEvents
    ) {
    }
}
