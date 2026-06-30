package com.LastBite.modules.analytics.repository;

import com.LastBite.modules.analytics.entity.StoreEngagementEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StoreEngagementEventRepository extends JpaRepository<StoreEngagementEvent, UUID> {

    @Query(value = """
        SELECT
            COUNT(*) FILTER (WHERE event_type = 'STORE_VIEW') AS "storeViews",
            COUNT(*) FILTER (WHERE event_type = 'STORE_CARD_CLICK') AS "storeCardClicks",
            COUNT(*) FILTER (WHERE event_type = 'BAG_VIEW') AS "bagViews",
            COUNT(*) FILTER (WHERE event_type = 'BAG_CARD_CLICK') AS "bagCardClicks",
            COUNT(DISTINCT user_id) AS "knownUsers",
            COUNT(DISTINCT session_id) FILTER (WHERE user_id IS NULL AND session_id IS NOT NULL) AS "anonymousSessions"
        FROM store_engagement_events
        WHERE store_id = :storeId
          AND occurred_at >= :from
          AND occurred_at < :to
    """, nativeQuery = true)
    EngagementSummaryProjection summarize(@Param("storeId") UUID storeId,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to);

    @Query(value = """
        SELECT
            CAST(EXTRACT(HOUR FROM occurred_at AT TIME ZONE :timezone) AS INTEGER) AS "hour",
            COUNT(*) FILTER (WHERE event_type = 'STORE_VIEW') AS "storeViews",
            COUNT(*) FILTER (WHERE event_type = 'BAG_VIEW') AS "bagViews",
            COUNT(*) FILTER (WHERE event_type IN ('STORE_CARD_CLICK', 'BAG_CARD_CLICK')) AS "cardClicks",
            COUNT(*) AS "totalEvents"
        FROM store_engagement_events
        WHERE store_id = :storeId
          AND occurred_at >= :from
          AND occurred_at < :to
        GROUP BY 1
        ORDER BY 1
    """, nativeQuery = true)
    List<HourlyEngagementProjection> hourly(@Param("storeId") UUID storeId,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to,
                                            @Param("timezone") String timezone);

    @Query(value = """
        SELECT
            TO_CHAR(occurred_at AT TIME ZONE :timezone, 'YYYY-MM-DD') AS "date",
            COUNT(*) FILTER (WHERE event_type = 'STORE_VIEW') AS "storeViews",
            COUNT(*) FILTER (WHERE event_type = 'BAG_VIEW') AS "bagViews",
            COUNT(*) FILTER (WHERE event_type IN ('STORE_CARD_CLICK', 'BAG_CARD_CLICK')) AS "cardClicks",
            COUNT(*) AS "totalEvents"
        FROM store_engagement_events
        WHERE store_id = :storeId
          AND occurred_at >= :from
          AND occurred_at < :to
        GROUP BY 1
        ORDER BY 1
    """, nativeQuery = true)
    List<DailyEngagementProjection> daily(@Param("storeId") UUID storeId,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to,
                                          @Param("timezone") String timezone);

    @Query(value = """
        SELECT COALESCE(NULLIF(source, ''), 'unknown') AS "source", COUNT(*) AS "totalEvents"
        FROM store_engagement_events
        WHERE store_id = :storeId
          AND occurred_at >= :from
          AND occurred_at < :to
        GROUP BY COALESCE(NULLIF(source, ''), 'unknown')
        ORDER BY totalEvents DESC
    """, nativeQuery = true)
    List<SourceEngagementProjection> sourceBreakdown(@Param("storeId") UUID storeId,
                                                     @Param("from") Instant from,
                                                     @Param("to") Instant to);

    @Query(value = """
        SELECT
            e.bag_id AS "bagId",
            MAX(b.name) AS "bagName",
            COUNT(*) FILTER (WHERE e.event_type = 'BAG_VIEW') AS "views",
            COUNT(*) FILTER (WHERE e.event_type = 'BAG_CARD_CLICK') AS "cardClicks",
            COUNT(*) AS "totalEvents"
        FROM store_engagement_events e
        JOIN surprise_bags b ON b.id = e.bag_id
        WHERE e.store_id = :storeId
          AND e.bag_id IS NOT NULL
          AND e.occurred_at >= :from
          AND e.occurred_at < :to
        GROUP BY e.bag_id
        ORDER BY totalEvents DESC
    """, nativeQuery = true)
    List<TopBagEngagementProjection> topBags(@Param("storeId") UUID storeId,
                                             @Param("from") Instant from,
                                             @Param("to") Instant to,
                                             Pageable pageable);
}
